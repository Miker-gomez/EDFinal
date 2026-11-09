package com.edfinal.service;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Materia;
import com.edfinal.domain.Tarea;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.TareaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TareaServiceTest {

    @Mock
    private TareaRepository tareaRepository;

    @Mock
    private MateriaRepository materiaRepository;

    private TareaService service;

    private final LocalDateTime ahora = LocalDateTime.now();
    private final Materia materiaEda = new Materia(1, "Estructuras de Datos", 10, "Profesor X");

    @BeforeEach
    void setUp() {
        service = new TareaService(tareaRepository, materiaRepository);
    }

    private Tarea crearTareaEjemplo(String id, int prioridad, int diasFuturos, EstadoTarea estado) {
        return new Tarea(
                id,
                "Tarea " + id,
                materiaEda.id(),
                materiaEda.nombre(),
                prioridad,
                ahora.plusDays(diasFuturos),
                estado
        );
    }

    @Test
    @DisplayName("inicializar carga tareas activas en MaxHeap y HashMap")
    void inicializar_cargaTareasEnEstructuras() {
        Tarea t1 = crearTareaEjemplo("t-1", 3, 2, EstadoTarea.PENDIENTE);
        Tarea t2 = crearTareaEjemplo("t-2", 5, 5, EstadoTarea.PENDIENTE);
        when(tareaRepository.listarActivas()).thenReturn(List.of(t1, t2));

        service.inicializar();

        assertThat(service.cantidadTareasEnHeap()).isEqualTo(2);
        assertThat(service.listarActivas()).hasSize(2);
        assertThat(service.obtenerMasUrgente()).contains(t2); // prioridad 5 > 3
    }

    @Test
    @DisplayName("obtenerMasUrgente retorna vacío cuando no hay tareas activas")
    void obtenerMasUrgente_sinTareas_retornaVacio() {
        when(tareaRepository.listarActivas()).thenReturn(List.of());
        service.inicializar();

        assertThat(service.obtenerMasUrgente()).isEmpty();
    }

    @Test
    @DisplayName("crearTarea persiste en BD e inserta en MaxHeap y HashMap")
    void crearTarea_validaPersisteYRegistraEnMemoria() {
        when(materiaRepository.buscarPorId(1)).thenReturn(Optional.of(materiaEda));

        LocalDateTime plazo = ahora.plusDays(3);
        Tarea creada = service.crearTarea("Taller Heaps", 1, 4, plazo);

        assertThat(creada.id()).isNotBlank();
        assertThat(creada.titulo()).isEqualTo("Taller Heaps");
        assertThat(creada.prioridad()).isEqualTo(4);
        assertThat(creada.estado()).isEqualTo(EstadoTarea.PENDIENTE);
        assertThat(creada.materia()).isEqualTo("Estructuras de Datos");

        verify(tareaRepository).insertar(any(Tarea.class));
        assertThat(service.obtenerMasUrgente()).contains(creada);
        assertThat(service.buscarPorId(creada.id())).contains(creada);
    }

    @Test
    @DisplayName("crearTarea rechaza fecha límite en el pasado")
    void crearTarea_fechaPasada_lanzaExcepcion() {
        assertThatThrownBy(() -> service.crearTarea("Taller Pasado", 1, 3, ahora.minusHours(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede ser anterior al momento actual");

        verify(tareaRepository, never()).insertar(any());
    }

    @Test
    @DisplayName("crearTarea rechaza materia inexistente")
    void crearTarea_materiaNoExiste_lanzaExcepcion() {
        when(materiaRepository.buscarPorId(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.crearTarea("Taller", 999, 3, ahora.plusDays(1)))
                .isInstanceOf(NoSuchElementException.class);

        verify(tareaRepository, never()).insertar(any());
    }

    @Test
    @DisplayName("completarTarea actualiza BD, remueve de heap y hashmap, y apila en deshacer")
    void completarTarea_actualizaYApilaEnDeshacer() {
        Tarea t1 = crearTareaEjemplo("t-1", 3, 2, EstadoTarea.PENDIENTE);
        when(tareaRepository.listarActivas()).thenReturn(List.of(t1));
        service.inicializar();

        Tarea completada = service.completarTarea("t-1");

        assertThat(completada.estado()).isEqualTo(EstadoTarea.COMPLETADA);
        verify(tareaRepository).actualizarEstado("t-1", EstadoTarea.COMPLETADA);

        assertThat(service.cantidadTareasEnHeap()).isZero();
        assertThat(service.obtenerMasUrgente()).isEmpty();
        assertThat(service.tamanoPilaDeshacer()).isEqualTo(1);
    }

    @Test
    @DisplayName("deshacer revierte el estado de completar restaurando en BD y memoria")
    void deshacer_revierteCompletar() {
        Tarea t1 = crearTareaEjemplo("t-1", 4, 3, EstadoTarea.PENDIENTE);
        when(tareaRepository.listarActivas()).thenReturn(List.of(t1));
        service.inicializar();

        service.completarTarea("t-1");
        assertThat(service.cantidadTareasEnHeap()).isZero();

        Optional<Tarea> revertida = service.deshacer();

        assertThat(revertida).isPresent();
        assertThat(revertida.get().id()).isEqualTo("t-1");
        assertThat(revertida.get().estado()).isEqualTo(EstadoTarea.PENDIENTE);

        verify(tareaRepository).actualizarEstado("t-1", EstadoTarea.PENDIENTE);
        assertThat(service.cantidadTareasEnHeap()).isEqualTo(1);
        assertThat(service.obtenerMasUrgente()).contains(t1);
        assertThat(service.tamanoPilaDeshacer()).isZero();
    }

    @Test
    @DisplayName("deshacer sin acciones pendientes devuelve vacío")
    void deshacer_pilaVacia_retornaVacio() {
        assertThat(service.deshacer()).isEmpty();
    }

    @Test
    @DisplayName("actualizarTarea ajusta prioridad, heap y mapa y registra en deshacer")
    void actualizarTarea_ajustaHeapYDeshacer() {
        Tarea t1 = crearTareaEjemplo("t-1", 2, 4, EstadoTarea.PENDIENTE);
        when(tareaRepository.listarActivas()).thenReturn(List.of(t1));
        when(materiaRepository.buscarPorId(1)).thenReturn(Optional.of(materiaEda));
        service.inicializar();

        LocalDateTime nuevaFecha = ahora.plusDays(1);
        Tarea actualizada = service.actualizarTarea("t-1", "Taller Avanzado", 1, 5, nuevaFecha);

        assertThat(actualizada.prioridad()).isEqualTo(5);
        assertThat(actualizada.titulo()).isEqualTo("Taller Avanzado");
        verify(tareaRepository).actualizar(any(Tarea.class));

        assertThat(service.obtenerMasUrgente()).contains(actualizada);
        assertThat(service.tamanoPilaDeshacer()).isEqualTo(1);

        // Deshacer la modificación restaura t1
        Optional<Tarea> revertida = service.deshacer();
        assertThat(revertida).contains(t1);
        assertThat(service.obtenerMasUrgente()).contains(t1);
    }

    @Test
    @DisplayName("actualizarTarea pasa de VENCIDA a PENDIENTE si la nueva fecha es futura")
    void actualizarTarea_vencidaAFechaFutura_pasaAPendiente() {
        Tarea tVencida = crearTareaEjemplo("t-v", 3, -1, EstadoTarea.VENCIDA);
        when(tareaRepository.listarActivas()).thenReturn(List.of(tVencida));
        when(materiaRepository.buscarPorId(1)).thenReturn(Optional.of(materiaEda));
        service.inicializar();

        LocalDateTime fechaFutura = ahora.plusDays(2);
        Tarea actualizada = service.actualizarTarea("t-v", "Taller Prorrogado", 1, 3, fechaFutura);

        assertThat(actualizada.estado()).isEqualTo(EstadoTarea.PENDIENTE);
        verify(tareaRepository).actualizar(actualizada);
    }

    @Test
    @DisplayName("sincronizarVencidas actualiza tareas expiradas en memoria")
    void sincronizarVencidas_actualizaMemoria() {
        LocalDateTime pasado = ahora.minusMinutes(5);
        Tarea tExpirada = new Tarea("t-exp", "Expirada", 1, "EDA", 3, pasado, EstadoTarea.PENDIENTE);
        when(tareaRepository.listarActivas()).thenReturn(List.of(tExpirada));
        when(tareaRepository.marcarVencidas()).thenReturn(1);
        service.inicializar();

        int marcadas = service.sincronizarVencidas();

        assertThat(marcadas).isEqualTo(1);
        Tarea enMemoria = service.buscarPorId("t-exp").orElseThrow();
        assertThat(enMemoria.estado()).isEqualTo(EstadoTarea.VENCIDA);
    }
}
