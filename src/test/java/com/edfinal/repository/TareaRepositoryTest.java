package com.edfinal.repository;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import com.edfinal.support.PostgresTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class TareaRepositoryTest extends PostgresTestBase {

    private final TareaRepository repo = new TareaRepository(jdbc);

    private int materiaId;

    @BeforeEach
    void crearMateriaBase() {
        materiaId = crearMateria("EDA"); // corre después de limpiarTablas() de la clase base
    }

    // ---------- insertar / buscar ----------

    @Test
    void insertarYBuscarPorId_roundTrip() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));

        repo.insertar(t);

        assertThat(repo.buscarPorId(t.id())).contains(t);
    }

    @Test
    void insertar_fechaPasada_triggerDePlazo_23514_yNoGuarda() {
        Tarea pasada = tarea(materiaId, "EDA", 3, enDias(-1));

        Throwable ex = catchThrowable(() -> repo.insertar(pasada));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23514");
        assertThat(repo.buscarPorId(pasada.id())).isEmpty();
    }

    @Test
    void insertar_materiaInexistente_fk_23503() {
        Tarea t = tarea(99999, "X", 3, enDias(2));

        Throwable ex = catchThrowable(() -> repo.insertar(t));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void insertar_prioridadFueraDeRango_fk_23503(int prioridad) {
        Tarea t = tarea(materiaId, "EDA", prioridad, enDias(2));

        Throwable ex = catchThrowable(() -> repo.insertar(t));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
    }

    @Test
    void insertar_tituloEnBlanco_check_23514() {
        Tarea t = new Tarea(UUID.randomUUID().toString(), "   ", materiaId, "EDA",
                3, enDias(2), EstadoTarea.PENDIENTE);

        Throwable ex = catchThrowable(() -> repo.insertar(t));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23514");
    }

    @Test
    void insertar_tituloNulo_notNull_23502() {
        Tarea t = new Tarea(UUID.randomUUID().toString(), null, materiaId, "EDA",
                3, enDias(2), EstadoTarea.PENDIENTE);

        Throwable ex = catchThrowable(() -> repo.insertar(t));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23502");
    }

    @Test
    void insertar_idDuplicado_pk_23505() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(2));
        repo.insertar(t);

        Throwable ex = catchThrowable(() -> repo.insertar(t));

        assertThat(ex).isInstanceOf(DuplicateKeyException.class);
        assertThat(sqlState(ex)).isEqualTo("23505");
    }

    @Test
    void buscarPorId_inexistente_devuelveVacio() {
        assertThat(repo.buscarPorId(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void buscarPorId_completada_tambienLaDevuelve() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA);

        assertThat(repo.buscarPorId(t.id())).contains(t.conEstado(EstadoTarea.COMPLETADA));
    }

    // ---------- listarActivas ----------

    @Test
    void listarActivas_incluyePendienteYVencida_excluyeCompletada() {
        Tarea a = tarea(materiaId, "EDA", 3, enDias(5));
        Tarea b = tarea(materiaId, "EDA", 4, enDias(6));
        Tarea c = tarea(materiaId, "EDA", 5, enDias(7));
        repo.insertar(a);
        repo.insertar(b);
        repo.insertar(c);
        repo.actualizarEstado(b.id(), EstadoTarea.VENCIDA);
        repo.actualizarEstado(c.id(), EstadoTarea.COMPLETADA);

        assertThat(repo.listarActivas()).containsExactlyInAnyOrder(a, b.conEstado(EstadoTarea.VENCIDA));
    }

    // ---------- actualizarEstado ----------

    @Test
    void actualizarEstado_cambiaYRestaura() {
        Tarea t = tarea(materiaId, "EDA", 4, enDias(5));
        repo.insertar(t);

        assertThat(repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA)).isTrue();
        assertThat(repo.actualizarEstado(t.id(), EstadoTarea.PENDIENTE)).isTrue();

        assertThat(repo.buscarPorId(t.id())).contains(t);
    }

    @Test
    void actualizarEstado_idInexistente_devuelveFalse() {
        assertThat(repo.actualizarEstado(UUID.randomUUID().toString(), EstadoTarea.COMPLETADA)).isFalse();
    }

    // ---------- actualizar (F09) ----------

    @Test
    void actualizar_cambiaLosCuatroCamposEditables() {
        Tarea t = tarea(materiaId, "EDA", 2, enDias(5));
        repo.insertar(t);
        int otraMateria = crearMateria("Cálculo");
        Tarea editada = new Tarea(t.id(), "Título nuevo", otraMateria, "Cálculo",
                5, enDias(10), EstadoTarea.PENDIENTE);

        assertThat(repo.actualizar(editada)).isTrue();

        assertThat(repo.buscarPorId(t.id())).contains(editada);
    }

    @Test
    void actualizar_idInexistente_devuelveFalse() {
        assertThat(repo.actualizar(tarea(materiaId, "EDA", 3, enDias(2)))).isFalse();
    }

    @Test
    void actualizar_materiaInexistente_fk_23503_yNoCambiaNada() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        Tarea mala = new Tarea(t.id(), t.titulo(), 99999, "X", 3, t.fechaLimite(), EstadoTarea.PENDIENTE);

        Throwable ex = catchThrowable(() -> repo.actualizar(mala));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
        assertThat(repo.buscarPorId(t.id())).contains(t);
    }

    @Test
    void actualizar_vencidaConFechaNueva_vuelveAPendiente_yAudita() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        forzarPlazoExpirado(t.id());
        repo.actualizarEstado(t.id(), EstadoTarea.VENCIDA);   // PENDIENTE -> VENCIDA
        Tarea reabierta = new Tarea(t.id(), t.titulo(), t.materiaId(), t.materia(),
                t.prioridad(), enDias(7), EstadoTarea.PENDIENTE);

        assertThat(repo.actualizar(reabierta)).isTrue();      // VENCIDA -> PENDIENTE

        assertThat(repo.buscarPorId(t.id())).contains(reabierta);
        assertThat(contarAuditoria(t.id())).isEqualTo(2);
    }

    @Test
    void actualizar_sinCambioDeEstado_noGeneraAuditoria() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        Tarea soloTitulo = new Tarea(t.id(), "Otro título", t.materiaId(), t.materia(),
                t.prioridad(), t.fechaLimite(), EstadoTarea.PENDIENTE);

        assertThat(repo.actualizar(soloTitulo)).isTrue();

        assertThat(contarAuditoria(t.id())).isZero();
    }
}