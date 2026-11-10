package com.edfinal.service;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import com.edfinal.exception.MateriaNoEncontradaException;
import com.edfinal.exception.NadaQueDeshacerException;
import com.edfinal.exception.TareaNoEncontradaException;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.TareaRepository;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

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
        assertTrue(ex instanceof MateriaNoEncontradaException);
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
        Tarea revertida = service.deshacer();

        // Assert
        assertEquals(t1.id(), revertida.id());
        assertEquals(EstadoTarea.PENDIENTE, revertida.estado());
        assertEquals(EstadoTarea.PENDIENTE, tareaRepository.buscarPorId(t1.id()).get().estado());
        assertEquals(1, service.cantidadTareasEnHeap());
        assertEquals(t1, service.obtenerMasUrgente().get());
        assertEquals(0, service.tamanoPilaDeshacer());

    }

    @Test
    @DisplayName("deshacer sin acciones pendientes lanza NadaQueDeshacerException")
    void deshacer_pilaVacia_lanzaExcepcion() {

        // Act
        Throwable ex = capturar(() -> service.deshacer());

        // Assert
        assertTrue(ex instanceof NadaQueDeshacerException);

    }

    @Test
    @DisplayName("actualizarTarea ajusta la prioridad en el heap y no se apila en deshacer")
    void actualizarTarea_ajustaHeapSinApilar() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 2, enDias(4));

        // Act
        Tarea actualizada = service.actualizarTarea(t1.id(), "Taller Avanzado", materiaId, 5, enDias(1));

        // Assert
        assertEquals(5, actualizada.prioridad());
        assertEquals("Taller Avanzado", actualizada.titulo());
        assertEquals(actualizada, service.obtenerMasUrgente().get());
        assertEquals(actualizada, tareaRepository.buscarPorId(t1.id()).get());
        assertEquals(0, service.tamanoPilaDeshacer());

    }

    @Test
    @DisplayName("actualizarTarea rechaza una fecha pasada y deja todo intacto")
    void actualizarTarea_fechaPasada_lanzaExcepcion() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 2, enDias(4));

        // Act
        Throwable ex = capturar(() -> service.actualizarTarea(t1.id(), "Taller", materiaId, 2, LocalDateTime.now().minusHours(1)));

        // Assert
        assertTrue(ex instanceof IllegalArgumentException);
        assertEquals(t1, service.buscarPorId(t1.id()).get());
        assertEquals(t1, tareaRepository.buscarPorId(t1.id()).get());

    }

    @Test
    @DisplayName("actualizarTarea sobre una tarea completada lanza TareaNoEncontradaException")
    void actualizarTarea_completada_lanzaExcepcion() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 2, enDias(4));
        service.completarTarea(t1.id());

        // Act
        Throwable ex = capturar(() -> service.actualizarTarea(t1.id(), "Taller", materiaId, 2, enDias(4)));

        // Assert
        assertTrue(ex instanceof TareaNoEncontradaException);

    }

    @Test
    @DisplayName("eliminarTarea saca la tarea del flujo activo y se puede deshacer")
    void eliminarTarea_yDeshacer() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 3, enDias(2));

        // Act
        service.eliminarTarea(t1.id());

        // Assert
        assertTrue(service.buscarPorId(t1.id()).isEmpty());
        assertEquals(EstadoTarea.COMPLETADA, tareaRepository.buscarPorId(t1.id()).get().estado());
        assertEquals(1, service.tamanoPilaDeshacer());

        // Act
        Tarea restaurada = service.deshacer();

        // Assert
        assertEquals(t1, restaurada);
        assertEquals(t1, service.obtenerMasUrgente().get());

    }

    @Test
    @DisplayName("deshacer relee la fila: conserva una edición hecha después de otra tarea")
    void deshacer_conservaEdicionPosterior() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 3, enDias(2));
        Tarea t2 = service.crearTarea("Parcial", materiaId, 2, enDias(3));
        service.completarTarea(t1.id());
        service.actualizarTarea(t2.id(), "Parcial final", materiaId, 4, enDias(3));

        // Act
        service.deshacer();

        // Assert
        assertEquals("Parcial final", service.buscarPorId(t2.id()).get().titulo());
        assertEquals(2, service.cantidadTareasEnHeap());

    }

    @Test
    @DisplayName("listarActivas devuelve de la más urgente a la menos urgente")
    void listarActivas_ordenaPorUrgencia() {

        // Arrange
        Tarea baja = service.crearTarea("Baja", materiaId, 1, enDias(1));
        Tarea alta = service.crearTarea("Alta", materiaId, 5, enDias(5));
        Tarea mediaCercana = service.crearTarea("Media cercana", materiaId, 3, enDias(1));
        Tarea mediaLejana = service.crearTarea("Media lejana", materiaId, 3, enDias(6));

        // Act
        List<Tarea> lista = service.listarActivas();

        // Assert
        assertEquals(List.of(alta, mediaCercana, mediaLejana, baja), lista);

    }

    @Test
    @DisplayName("buscarPorId no devuelve tareas completadas")
    void buscarPorId_completada_estaVacio() {

        // Arrange
        Tarea t1 = service.crearTarea("Taller", materiaId, 3, enDias(2));

        // Act
        service.completarTarea(t1.id());

        // Assert
        assertTrue(service.buscarPorId(t1.id()).isEmpty());

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
