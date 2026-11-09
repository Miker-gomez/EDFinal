package com.edfinal.service;

import com.edfinal.domain.EstadisticaMateria;
import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Materia;
import com.edfinal.domain.Tarea;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.TareaRepository;
import com.edfinal.structures.MaxHeap;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Servicio principal de gestión académica y orquestador de estructuras de datos.
 * <p>
 * Mantiene la sincronización bidireccional entre PostgreSQL (fuente de verdad)
 * y tres estructuras de datos en memoria:
 * <ul>
 *   <li><b>MaxHeap propio:</b> consulta y extracción de la tarea más urgente en O(1) / O(log n).</li>
 *   <li><b>HashMap:</b> búsqueda directa por UUID en O(1).</li>
 *   <li><b>Pila (ArrayDeque):</b> reversión de la última acción (completar o modificar) en LIFO O(1).</li>
 * </ul>
 * Toda mutación en memoria está protegida contra acceso concurrente mediante {@link ReentrantReadWriteLock}.
 */
@Service
public class TareaService {

    private final TareaRepository tareaRepository;
    private final MateriaRepository materiaRepository;

    private final MaxHeap<Tarea> heap = new MaxHeap<>(Tarea::id);
    private final Map<String, Tarea> mapaActivas = new HashMap<>();
    private final Deque<AccionDeshacer> pilaDeshacer = new ArrayDeque<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public TareaService(TareaRepository tareaRepository, MateriaRepository materiaRepository) {
        this.tareaRepository = tareaRepository;
        this.materiaRepository = materiaRepository;
    }

    /**
     * Carga el estado inicial de tareas activas desde PostgreSQL hacia el MaxHeap y HashMap.
     */
    @PostConstruct
    public void inicializar() {
        recargarDesdeBaseDeDatos();
    }

    /**
     * Recarga las tareas activas (PENDIENTE y VENCIDA) desde la base de datos a las estructuras en memoria.
     */
    public void recargarDesdeBaseDeDatos() {
        lock.writeLock().lock();
        try {
            while (!heap.isEmpty()) {
                heap.extractMax();
            }
            mapaActivas.clear();
            pilaDeshacer.clear();

            List<Tarea> activas = tareaRepository.listarActivas();
            for (Tarea t : activas) {
                heap.insert(t);
                mapaActivas.put(t.id(), t);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Obtiene la tarea más urgente en O(1) usando el MaxHeap.
     */
    public Optional<Tarea> obtenerMasUrgente() {
        lock.readLock().lock();
        try {
            return heap.peekMax();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Busca una tarea activa por su ID en O(1) usando el HashMap en memoria.
     * Si no está en memoria (por ejemplo, si ya fue completada), consulta PostgreSQL.
     */
    public Optional<Tarea> buscarPorId(String id) {
        lock.readLock().lock();
        try {
            Tarea enMemoria = mapaActivas.get(id);
            if (enMemoria != null) {
                return Optional.of(enMemoria);
            }
        } finally {
            lock.readLock().unlock();
        }
        return tareaRepository.buscarPorId(id);
    }

    /**
     * Retorna una copia de la lista de tareas activas presentes en memoria.
     */
    public List<Tarea> listarActivas() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(mapaActivas.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Crea una nueva tarea y la sincroniza en PostgreSQL y en las estructuras en memoria.
     *
     * @param titulo      Título descriptivo no vacío.
     * @param materiaId   ID de una materia existente.
     * @param prioridad   Nivel de prioridad entre 1 y 5.
     * @param fechaLimite Fecha y hora de vencimiento (debe ser futura).
     * @return La tarea persistida y registrada en memoria.
     */
    public Tarea crearTarea(String titulo, int materiaId, int prioridad, LocalDateTime fechaLimite) {
        Objects.requireNonNull(titulo, "El título no puede ser nulo");
        Objects.requireNonNull(fechaLimite, "La fecha límite no puede ser nula");
        if (titulo.isBlank()) {
            throw new IllegalArgumentException("El título no puede estar vacío");
        }
        if (prioridad < 1 || prioridad > 5) {
            throw new IllegalArgumentException("La prioridad debe estar entre 1 y 5");
        }
        if (fechaLimite.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("La fecha límite no puede ser anterior al momento actual");
        }

        Materia materia = materiaRepository.buscarPorId(materiaId)
                .orElseThrow(() -> new NoSuchElementException("La materia con ID " + materiaId + " no existe"));

        String id = UUID.randomUUID().toString();
        Tarea nueva = new Tarea(id, titulo.trim(), materiaId, materia.nombre(), prioridad, fechaLimite, EstadoTarea.PENDIENTE);

        lock.writeLock().lock();
        try {
            tareaRepository.insertar(nueva);
            heap.insert(nueva);
            mapaActivas.put(nueva.id(), nueva);
            return nueva;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Marca una tarea como COMPLETADA:
     * - Actualiza su estado en PostgreSQL.
     * - La remueve del MaxHeap y del HashMap de tareas activas.
     * - Apila la acción en la pila de deshacer (ArrayDeque).
     *
     * @param id Identificador de la tarea.
     * @return La tarea con su estado actualizado a COMPLETADA.
     */
    public Tarea completarTarea(String id) {
        lock.writeLock().lock();
        try {
            Tarea actual = mapaActivas.get(id);
            if (actual == null) {
                actual = tareaRepository.buscarPorId(id)
                        .orElseThrow(() -> new NoSuchElementException("Tarea no encontrada con ID: " + id));
            }

            if (actual.estado() == EstadoTarea.COMPLETADA) {
                return actual;
            }

            tareaRepository.actualizarEstado(id, EstadoTarea.COMPLETADA);

            heap.remove(id);
            mapaActivas.remove(id);

            pilaDeshacer.push(new AccionDeshacer(AccionDeshacer.TipoAccion.COMPLETAR, actual));

            return actual.conEstado(EstadoTarea.COMPLETADA);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Actualiza los datos de una tarea (F09).
     * Si la tarea estaba VENCIDA y su nueva fecha límite es futura, pasa automáticamente a PENDIENTE.
     * Sincroniza la modificación en PostgreSQL y actualiza su posición en el MaxHeap y HashMap.
     */
    public Tarea actualizarTarea(String id, String nuevoTitulo, int nuevaMateriaId, int nuevaPrioridad, LocalDateTime nuevaFechaLimite) {
        Objects.requireNonNull(nuevoTitulo, "El título no puede ser nulo");
        Objects.requireNonNull(nuevaFechaLimite, "La fecha límite no puede ser nula");
        if (nuevoTitulo.isBlank()) {
            throw new IllegalArgumentException("El título no puede estar vacío");
        }
        if (nuevaPrioridad < 1 || nuevaPrioridad > 5) {
            throw new IllegalArgumentException("La prioridad debe estar entre 1 y 5");
        }

        Materia materia = materiaRepository.buscarPorId(nuevaMateriaId)
                .orElseThrow(() -> new NoSuchElementException("La materia con ID " + nuevaMateriaId + " no existe"));

        lock.writeLock().lock();
        try {
            Tarea actual = mapaActivas.get(id);
            if (actual == null) {
                actual = tareaRepository.buscarPorId(id)
                        .orElseThrow(() -> new NoSuchElementException("Tarea no encontrada con ID: " + id));
            }

            // Regla de transición de estado al modificar plazo:
            EstadoTarea nuevoEstado = actual.estado();
            if (actual.estado() == EstadoTarea.VENCIDA && nuevaFechaLimite.isAfter(LocalDateTime.now())) {
                nuevoEstado = EstadoTarea.PENDIENTE;
            }

            Tarea modificada = new Tarea(
                    id,
                    nuevoTitulo.trim(),
                    nuevaMateriaId,
                    materia.nombre(),
                    nuevaPrioridad,
                    nuevaFechaLimite,
                    nuevoEstado
            );

            tareaRepository.actualizar(modificada);

            // Reajustar en MaxHeap y HashMap
            heap.remove(id);
            if (nuevoEstado != EstadoTarea.COMPLETADA) {
                heap.insert(modificada);
                mapaActivas.put(id, modificada);
            } else {
                mapaActivas.remove(id);
            }

            pilaDeshacer.push(new AccionDeshacer(AccionDeshacer.TipoAccion.MODIFICAR, actual));

            return modificada;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Deshace la última acción registrada en la pila (LIFO).
     * Restaura el estado previo tanto en PostgreSQL como en las estructuras en memoria.
     *
     * @return La tarea restaurada a su estado anterior, o {@link Optional#empty()} si la pila está vacía.
     */
    public Optional<Tarea> deshacer() {
        lock.writeLock().lock();
        try {
            if (pilaDeshacer.isEmpty()) {
                return Optional.empty();
            }

            AccionDeshacer accion = pilaDeshacer.pop();
            Tarea previa = accion.tareaPrevia();

            switch (accion.tipo()) {
                case COMPLETAR -> {
                    // Volver al estado previo (PENDIENTE o VENCIDA)
                    tareaRepository.actualizarEstado(previa.id(), previa.estado());
                    heap.remove(previa.id());
                    heap.insert(previa);
                    mapaActivas.put(previa.id(), previa);
                }
                case MODIFICAR -> {
                    tareaRepository.actualizar(previa);
                    heap.remove(previa.id());
                    if (previa.estado() != EstadoTarea.COMPLETADA) {
                        heap.insert(previa);
                        mapaActivas.put(previa.id(), previa);
                    } else {
                        mapaActivas.remove(previa.id());
                    }
                }
            }

            return Optional.of(previa);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Ejecuta el procedimiento almacenado sp_marcar_vencidas y actualiza el estado en memoria
     * de aquellas tareas activas cuya fecha límite ya haya expirado.
     *
     * @return Cantidad de tareas marcadas como vencidas.
     */
    public int sincronizarVencidas() {
        lock.writeLock().lock();
        try {
            int marcadas = tareaRepository.marcarVencidas();
            if (marcadas > 0) {
                LocalDateTime ahora = LocalDateTime.now();
                List<Tarea> aActualizar = new ArrayList<>();
                for (Tarea t : mapaActivas.values()) {
                    if (t.estado() == EstadoTarea.PENDIENTE && t.fechaLimite().isBefore(ahora)) {
                        aActualizar.add(t);
                    }
                }
                for (Tarea vencida : aActualizar) {
                    Tarea actualizada = vencida.conEstado(EstadoTarea.VENCIDA);
                    heap.remove(vencida.id());
                    heap.insert(actualizada);
                    mapaActivas.put(vencida.id(), actualizada);
                }
            }
            return marcadas;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Retorna las estadísticas consolidadas por materia invocando sp_estadisticas_entrega().
     */
    public List<EstadisticaMateria> obtenerEstadisticasPorMateria() {
        return tareaRepository.estadisticasPorMateria();
    }

    /**
     * Retorna la cantidad de acciones disponibles para deshacer en la pila.
     */
    public int tamanoPilaDeshacer() {
        lock.readLock().lock();
        try {
            return pilaDeshacer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Retorna el número de elementos activos en el MaxHeap.
     */
    public int cantidadTareasEnHeap() {
        lock.readLock().lock();
        try {
            return heap.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
