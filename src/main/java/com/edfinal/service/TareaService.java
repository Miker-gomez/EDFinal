package com.edfinal.service;

import com.edfinal.domain.EstadisticaMateria;
import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Materia;
import com.edfinal.domain.Tarea;
import com.edfinal.exception.MateriaNoEncontradaException;
import com.edfinal.exception.NadaQueDeshacerException;
import com.edfinal.exception.TareaNoEncontradaException;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.TareaRepository;
import com.edfinal.structures.MaxHeap;
import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Stack;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// Sincroniza PostgreSQL (fuente de verdad) con el MaxHeap, el HashMap y la pila de deshacer
@Log4j2
@Service
public class TareaService {

    // PostgreSQL no admite fechas más allá del año 294276; se acota a un valor razonable
    private static final int ANIO_MAXIMO = 9999;

    private final TareaRepository tareaRepository;
    private final MateriaRepository materiaRepository;

    private final MaxHeap<Tarea> heap = new MaxHeap<>();
    private final Map<String, Tarea> mapaActivas = new HashMap<>();
    private final Stack<AccionDeshacer> pilaDeshacer = new Stack<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public TareaService(TareaRepository tareaRepository, MateriaRepository materiaRepository) {

        this.tareaRepository = tareaRepository;
        this.materiaRepository = materiaRepository;

    }

    // Carga el estado inicial de tareas activas desde PostgreSQL hacia el MaxHeap y HashMap.
    @PostConstruct
    public void inicializar() {

        recargarDesdeBaseDeDatos();
        log.info("Estructuras reconstruidas al arrancar. Tareas activas en MaxHeap y HashMap: {}", cantidadTareasEnHeap());

    }

    // Recarga las tareas activas (PENDIENTE y VENCIDA) desde la base de datos; la pila no se toca
    public void recargarDesdeBaseDeDatos() {

        lock.writeLock().lock();
        try {

            while (!heap.isEmpty()) {

                heap.extractMax();

            }
            mapaActivas.clear();

            List<Tarea> activas = tareaRepository.listarActivas();
            for (Tarea t : activas) {

                heap.insert(t);
                mapaActivas.put(t.id(), t);

            }
            log.debug("Estructuras recargadas desde la base de datos. Tareas activas: {}", heap.size());

        } finally {
            lock.writeLock().unlock();
        }

    }

    // Obtiene la tarea más urgente en O(1) usando el MaxHeap.
    public Optional<Tarea> obtenerMasUrgente() {

        lock.readLock().lock();
        try {

            return Optional.ofNullable(heap.peekMax());

        } finally {
            lock.readLock().unlock();
        }

    }

    // Busca una tarea activa por su ID en O(1) usando el HashMap en memoria.
    public Optional<Tarea> buscarPorId(String id) {

        lock.readLock().lock();
        try {

            return Optional.ofNullable(mapaActivas.get(id));

        } finally {
            lock.readLock().unlock();
        }

    }

    // Retorna las tareas activas de la más urgente a la menos urgente.
    public List<Tarea> listarActivas() {

        lock.readLock().lock();
        try {

            List<Tarea> lista = new ArrayList<>(mapaActivas.values());
            Collections.sort(lista, Collections.reverseOrder());
            return lista;

        } finally {
            lock.readLock().unlock();
        }

    }

    // Crea una nueva tarea y la sincroniza en PostgreSQL y en las estructuras en memoria.
    public Tarea crearTarea(String titulo, int materiaId, int prioridad, LocalDateTime fechaLimite) {

        validarDatos(titulo, prioridad, fechaLimite);
        if (fechaLimite.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La fecha límite no puede ser anterior al momento actual");
        }

        lock.writeLock().lock();
        try {

            // La materia se lee dentro del lock para que un renombrado concurrente no deje un nombre viejo en memoria
            Materia materia = buscarMateria(materiaId);
            String id = UUID.randomUUID().toString();
            Tarea nueva = new Tarea(id, titulo.trim(), materiaId, materia.nombre(), prioridad,
                    fechaLimite.truncatedTo(ChronoUnit.MICROS), EstadoTarea.PENDIENTE);

            // Primero la base de datos; si falla, la memoria queda intacta
            tareaRepository.insertar(nueva);
            heap.insert(nueva);
            mapaActivas.put(nueva.id(), nueva);
            log.info("Tarea {} insertada en MaxHeap. Nuevo tamaño: {}", nueva.id(), heap.size());
            return nueva;

        } finally {
            lock.writeLock().unlock();
        }

    }

    // Edita una tarea activa (F09). No se registra en la pila de deshacer.
    public Tarea actualizarTarea(String id, String nuevoTitulo, int nuevaMateriaId, int nuevaPrioridad, LocalDateTime nuevaFechaLimite) {

        validarDatos(nuevoTitulo, nuevaPrioridad, nuevaFechaLimite);
        LocalDateTime fechaNormalizada = nuevaFechaLimite.truncatedTo(ChronoUnit.MICROS);

        lock.writeLock().lock();
        try {

            Materia materia = buscarMateria(nuevaMateriaId);
            Tarea actual = mapaActivas.get(id);
            if (actual == null) {
                throw new TareaNoEncontradaException("Tarea no encontrada");
            }

            // Si la fecha cambia debe ser futura y la tarea vuelve a PENDIENTE
            EstadoTarea nuevoEstado = actual.estado();
            if (!fechaNormalizada.equals(actual.fechaLimite())) {

                if (fechaNormalizada.isBefore(LocalDateTime.now())) {
                    throw new IllegalArgumentException("La fecha límite no puede ser anterior al momento actual");
                }
                nuevoEstado = EstadoTarea.PENDIENTE;

            }

            Tarea modificada = new Tarea(id, nuevoTitulo.trim(), nuevaMateriaId, materia.nombre(),
                    nuevaPrioridad, fechaNormalizada, nuevoEstado);

            // Primero la base de datos; luego se relee la fila para guardar lo que quedó persistido
            if (!tareaRepository.actualizar(modificada)) {
                throw new TareaNoEncontradaException("Tarea no encontrada");
            }
            Tarea guardada = tareaRepository.buscarPorId(id)
                    .orElseThrow(() -> new TareaNoEncontradaException("Tarea no encontrada"));

            heap.remove(id);
            heap.insert(guardada);
            mapaActivas.put(id, guardada);
            log.info("Tarea {} editada y reubicada en MaxHeap. Tamaño: {}", id, heap.size());
            return guardada;

        } finally {
            lock.writeLock().unlock();
        }

    }

    // Completar y eliminar sacan la tarea del flujo activo; solo cambia el tipo de acción apilada
    public Tarea completarTarea(String id) {

        return sacarDelFlujoActivo(id, AccionDeshacer.TipoAccion.COMPLETAR);

    }

    public void eliminarTarea(String id) {

        sacarDelFlujoActivo(id, AccionDeshacer.TipoAccion.ELIMINAR);

    }

    // Revierte la última acción de la pila (LIFO) y devuelve la tarea restaurada.
    public Tarea deshacer() {

        lock.writeLock().lock();
        try {

            if (pilaDeshacer.isEmpty()) {
                throw new NadaQueDeshacerException("No hay acciones para deshacer");
            }

            // Se mira la cima y solo se desapila cuando la base de datos ya aceptó el cambio
            AccionDeshacer accion = pilaDeshacer.peek();
            Tarea previa = accion.tareaPrevia();

            tareaRepository.actualizarEstado(previa.id(), previa.estado());
            Tarea restaurada = tareaRepository.buscarPorId(previa.id())
                    .orElseThrow(() -> new TareaNoEncontradaException("Tarea no encontrada"));
            pilaDeshacer.pop();

            heap.remove(restaurada.id());
            heap.insert(restaurada);
            mapaActivas.put(restaurada.id(), restaurada);
            log.info("Acción {} deshecha y desapilada. Tarea {} reinsertada en MaxHeap. Tamaño del heap: {}. Tamaño de la pila: {}",
                    accion.tipo(), restaurada.id(), heap.size(), pilaDeshacer.size());
            return restaurada;

        } finally {
            lock.writeLock().unlock();
        }

    }

    // Ejecuta sp_marcar_vencidas y recarga las estructuras bajo el mismo lock
    public int sincronizarVencidas() {

        lock.writeLock().lock();
        try {

            int marcadas = tareaRepository.marcarVencidas();
            recargarDesdeBaseDeDatos();
            log.info("Tareas marcadas como vencidas: {}", marcadas);
            return marcadas;

        } finally {
            lock.writeLock().unlock();
        }

    }

    // Retorna las estadísticas consolidadas por materia invocando sp_estadisticas_entrega().
    public List<EstadisticaMateria> obtenerEstadisticasPorMateria() {

        return tareaRepository.estadisticasPorMateria();

    }

    // Retorna la cantidad de acciones disponibles para deshacer en la pila.
    public int tamanoPilaDeshacer() {

        lock.readLock().lock();
        try {

            return pilaDeshacer.size();

        } finally {
            lock.readLock().unlock();
        }

    }

    // Retorna el número de elementos activos en el MaxHeap.
    public int cantidadTareasEnHeap() {

        lock.readLock().lock();
        try {

            return heap.size();

        } finally {
            lock.readLock().unlock();
        }

    }

    private Tarea sacarDelFlujoActivo(String id, AccionDeshacer.TipoAccion tipo) {

        lock.writeLock().lock();
        try {

            Tarea actual = mapaActivas.get(id);
            if (actual == null) {
                throw new TareaNoEncontradaException("Tarea no encontrada");
            }

            // Primero la base de datos; si falla, el heap y el mapa quedan intactos
            tareaRepository.actualizarEstado(id, EstadoTarea.COMPLETADA);
            heap.remove(id);
            mapaActivas.remove(id);
            pilaDeshacer.push(new AccionDeshacer(tipo, actual));
            log.info("Tarea {} retirada del MaxHeap por {} y apilada en deshacer. Tamaño del heap: {}. Tamaño de la pila: {}",
                    id, tipo, heap.size(), pilaDeshacer.size());
            return actual.conEstado(EstadoTarea.COMPLETADA);

        } finally {
            lock.writeLock().unlock();
        }

    }

    private void validarDatos(String titulo, int prioridad, LocalDateTime fechaLimite) {

        Objects.requireNonNull(titulo, "El título no puede ser nulo");
        Objects.requireNonNull(fechaLimite, "La fecha límite no puede ser nula");
        if (titulo.trim().isEmpty()) {
            throw new IllegalArgumentException("El título no puede estar vacío");
        }
        if (titulo.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("El título contiene caracteres no válidos");
        }
        if (fechaLimite.getYear() > ANIO_MAXIMO) {
            throw new IllegalArgumentException("La fecha límite es demasiado lejana");
        }
        if (prioridad < 1 || prioridad > 5) {
            throw new IllegalArgumentException("La prioridad debe estar entre 1 y 5");
        }

    }

    private Materia buscarMateria(int materiaId) {

        return materiaRepository.buscarPorId(materiaId)
                .orElseThrow(() -> new MateriaNoEncontradaException("La materia con ID " + materiaId + " no existe"));

    }
}
