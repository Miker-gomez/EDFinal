package com.edfinal.service;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.TareaRepository;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TareaServiceTest extends PostgresTestBase {

    private TareaRepository tareaRepository;
    private TareaService service;
    private int materiaId;

    @BeforeEach
    void setUp() {

        // Arrange
        tareaRepository = new TareaRepository(jdbc);
        service = new TareaService(tareaRepository, new MateriaRepository(jdbc));
        materiaId = crearMateria("Estructuras de Datos");
        service.inicializar();

    }

    @Test
    @DisplayName("inicializar carga las tareas activas en el MaxHeap y el HashMap")
    void inicializar_cargaTareasEnEstructuras() {

        // Arrange
        Tarea t1 = tarea(materiaId, "Estructuras de Datos", 3, enDias(2));
        Tarea t2 = tarea(materiaId, "Estructuras de Datos", 5, enDias(5));
        tareaRepository.insertar(t1);
        tareaRepository.insertar(t2);

        // Act
        service.inicializar();

        // Assert
        assertEquals(2, service.cantidadTareasEnHeap());
        assertEquals(2, service.listarActivas().size());
        assertEquals(t2, service.obtenerMasUrgente().get());

    }

    @Test
    @DisplayName("obtenerMasUrgente está vacío cuando no hay tareas activas")
    void obtenerMasUrgente_sinTareas_retornaVacio() {

        // Assert
        assertTrue(service.obtenerMasUrgente().isEmpty());

    }

    @Test
    @DisplayName("crearTarea persiste en la BD y registra en el heap y el mapa")
    void crearTarea_validaPersisteYRegistraEnMemoria() {

        // Act
        Tarea creada = service.crearTarea("Taller Heaps", materiaId, 4, enDias(3));

        // Assert
        assertFalse(creada.id().trim().isEmpty());
        assertEquals("Taller Heaps", creada.titulo());
        assertEquals(4, creada.prioridad());
        assertEquals(EstadoTarea.PENDIENTE, creada.estado());
        assertEquals("Estructuras de Datos", creada.materia());
        assertEquals(creada, tareaRepository.buscarPorId(creada.id()).get());
        assertEquals(creada, service.obtenerMasUrgente().get());
        assertEquals(creada, service.buscarPorId(creada.id()).get());

    }

    @Test
    @DisplayName("crearTarea rechaza una fecha límite en el pasado")
    void crearTarea_fechaPasada_lanzaExcepcion() {

        // Act
        Throwable ex = capturar(() -> service.crearTarea("Taller Pasado", materiaId, 3, LocalDateTime.now().minusHours(1)));

        // Assert
        assertTrue(ex instanceof IllegalArgumentException);
        assertTrue(ex.getMessage().contains("no puede ser anterior al momento actual"));
        assertTrue(tareaRepository.listarActivas().isEmpty());

    }

    @Test
    @DisplayName("crearTarea rechaza una materia inexistente")
    void crearTarea_materiaNoExiste_lanzaExcepcion() {

        // Act
        Throwable ex = capturar(() -> service.crearTarea("Taller", 99999, 3, enDias(1)));

        // Assert
        assertTrue(ex instanceof NoSuchElementException);
        assertTrue(tareaRepository.listarActivas().isEmpty());

    }

    @Test
    @DisplayName("completarTarea actualiza la BD, saca la tarea del heap y apila en deshacer")
    void completarTarea_actualizaYApilaEnDeshacer() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 3, enDias(2));

        // Act
        Tarea completada = service.completarTarea(t1.id());

        // Assert
        assertEquals(EstadoTarea.COMPLETADA, completada.estado());
        assertEquals(EstadoTarea.COMPLETADA, tareaRepository.buscarPorId(t1.id()).get().estado());
        assertEquals(0, service.cantidadTareasEnHeap());
        assertTrue(service.obtenerMasUrgente().isEmpty());
        assertEquals(1, service.tamanoPilaDeshacer());

    }

    @Test
    @DisplayName("deshacer revierte completar en la BD y en memoria")
    void deshacer_revierteCompletar() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 4, enDias(3));
        service.completarTarea(t1.id());

        // Act
        Optional<Tarea> revertida = service.deshacer();

        // Assert
        assertTrue(revertida.isPresent());
        assertEquals(t1.id(), revertida.get().id());
        assertEquals(EstadoTarea.PENDIENTE, revertida.get().estado());
        assertEquals(EstadoTarea.PENDIENTE, tareaRepository.buscarPorId(t1.id()).get().estado());
        assertEquals(1, service.cantidadTareasEnHeap());
        assertEquals(t1, service.obtenerMasUrgente().get());
        assertEquals(0, service.tamanoPilaDeshacer());

    }

    @Test
    @DisplayName("deshacer sin acciones pendientes devuelve vacío")
    void deshacer_pilaVacia_retornaVacio() {

        // Assert
        assertTrue(service.deshacer().isEmpty());

    }

    @Test
    @DisplayName("actualizarTarea ajusta la prioridad en el heap y se puede deshacer")
    void actualizarTarea_ajustaHeapYDeshacer() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 2, enDias(4));

        // Act
        Tarea actualizada = service.actualizarTarea(t1.id(), "Taller Avanzado", materiaId, 5, enDias(1));

        // Assert
        assertEquals(5, actualizada.prioridad());
        assertEquals("Taller Avanzado", actualizada.titulo());
        assertEquals(actualizada, service.obtenerMasUrgente().get());
        assertEquals(1, service.tamanoPilaDeshacer());

        // Act
        Optional<Tarea> revertida = service.deshacer();

        // Assert
        assertEquals(t1, revertida.get());
        assertEquals(t1, service.obtenerMasUrgente().get());

    }

    @Test
    @DisplayName("actualizarTarea pasa de VENCIDA a PENDIENTE si la nueva fecha es futura")
    void actualizarTarea_vencidaAFechaFutura_pasaAPendiente() {

        // Arrange
        Tarea t = service.crearTarea("Taller", materiaId, 3, enDias(1));
        forzarPlazoExpirado(t.id());
        tareaRepository.marcarVencidas();
        service.inicializar();
        assertEquals(EstadoTarea.VENCIDA, service.buscarPorId(t.id()).get().estado());

        // Act
        Tarea actualizada = service.actualizarTarea(t.id(), "Taller Prorrogado", materiaId, 3, enDias(2));

        // Assert
        assertEquals(EstadoTarea.PENDIENTE, actualizada.estado());
        assertEquals(EstadoTarea.PENDIENTE, tareaRepository.buscarPorId(t.id()).get().estado());

    }

    @Test
    @DisplayName("sincronizarVencidas actualiza en memoria las tareas expiradas")
    void sincronizarVencidas_actualizaMemoria() {

        // Arrange
        Tarea t = service.crearTarea("Expirada", materiaId, 3, enDias(1));
        forzarPlazoExpirado(t.id());
        service.inicializar();

        // Act
        int marcadas = service.sincronizarVencidas();

        // Assert
        assertEquals(1, marcadas);
        assertEquals(EstadoTarea.VENCIDA, service.buscarPorId(t.id()).get().estado());

    }

}
