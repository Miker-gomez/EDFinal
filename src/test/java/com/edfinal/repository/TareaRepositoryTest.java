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

import java.util.List;
import java.util.UUID;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertEquals(t, repo.buscarPorId(t.id()).get());
    }

    @Test
    void insertar_fechaPasada_triggerDePlazo_23514_yNoGuarda() {
        Tarea pasada = tarea(materiaId, "EDA", 3, enDias(-1));

        Throwable ex = capturar(() -> repo.insertar(pasada));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23514", sqlState(ex));
        assertTrue(repo.buscarPorId(pasada.id()).isEmpty());
    }

    @Test
    void insertar_materiaInexistente_fk_23503() {
        Tarea t = tarea(99999, "X", 3, enDias(2));

        Throwable ex = capturar(() -> repo.insertar(t));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void insertar_prioridadFueraDeRango_fk_23503(int prioridad) {
        Tarea t = tarea(materiaId, "EDA", prioridad, enDias(2));

        Throwable ex = capturar(() -> repo.insertar(t));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
    }

    @Test
    void insertar_tituloEnBlanco_check_23514() {
        Tarea t = new Tarea(UUID.randomUUID().toString(), "   ", materiaId, "EDA",
                3, enDias(2), EstadoTarea.PENDIENTE);

        Throwable ex = capturar(() -> repo.insertar(t));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23514", sqlState(ex));
    }

    @Test
    void insertar_tituloNulo_notNull_23502() {
        Tarea t = new Tarea(UUID.randomUUID().toString(), null, materiaId, "EDA",
                3, enDias(2), EstadoTarea.PENDIENTE);

        Throwable ex = capturar(() -> repo.insertar(t));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23502", sqlState(ex));
    }

    @Test
    void insertar_idDuplicado_pk_23505() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(2));
        repo.insertar(t);

        Throwable ex = capturar(() -> repo.insertar(t));

        assertTrue(ex instanceof DuplicateKeyException);
        assertEquals("23505", sqlState(ex));
    }

    @Test
    void buscarPorId_inexistente_devuelveVacio() {
        assertTrue(repo.buscarPorId(UUID.randomUUID().toString()).isEmpty());
    }

    @Test
    void buscarPorId_completada_tambienLaDevuelve() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA);

        assertEquals(t.conEstado(EstadoTarea.COMPLETADA), repo.buscarPorId(t.id()).get());
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

        assertTrue(repo.listarActivas().containsAll(List.of(a, b.conEstado(EstadoTarea.VENCIDA))));
        assertEquals(List.of(a, b.conEstado(EstadoTarea.VENCIDA)).size(), repo.listarActivas().size());
    }

    // ---------- actualizarEstado ----------

    @Test
    void actualizarEstado_cambiaYRestaura() {
        Tarea t = tarea(materiaId, "EDA", 4, enDias(5));
        repo.insertar(t);

        assertTrue(repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA));
        assertTrue(repo.actualizarEstado(t.id(), EstadoTarea.PENDIENTE));

        assertEquals(t, repo.buscarPorId(t.id()).get());
    }

    @Test
    void actualizarEstado_idInexistente_devuelveFalse() {
        assertFalse(repo.actualizarEstado(UUID.randomUUID().toString(), EstadoTarea.COMPLETADA));
    }

    // ---------- actualizar (F09) ----------

    @Test
    void actualizar_cambiaLosCuatroCamposEditables() {
        Tarea t = tarea(materiaId, "EDA", 2, enDias(5));
        repo.insertar(t);
        int otraMateria = crearMateria("Cálculo");
        Tarea editada = new Tarea(t.id(), "Título nuevo", otraMateria, "Cálculo",
                5, enDias(10), EstadoTarea.PENDIENTE);

        assertTrue(repo.actualizar(editada));

        assertEquals(editada, repo.buscarPorId(t.id()).get());
    }

    @Test
    void actualizar_idInexistente_devuelveFalse() {
        assertFalse(repo.actualizar(tarea(materiaId, "EDA", 3, enDias(2))));
    }

    @Test
    void actualizar_materiaInexistente_fk_23503_yNoCambiaNada() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        Tarea mala = new Tarea(t.id(), t.titulo(), 99999, "X", 3, t.fechaLimite(), EstadoTarea.PENDIENTE);

        Throwable ex = capturar(() -> repo.actualizar(mala));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
        assertEquals(t, repo.buscarPorId(t.id()).get());
    }

    @Test
    void actualizar_vencidaConFechaNueva_vuelveAPendiente_yAudita() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        forzarPlazoExpirado(t.id());
        repo.actualizarEstado(t.id(), EstadoTarea.VENCIDA);   // PENDIENTE -> VENCIDA
        Tarea reabierta = new Tarea(t.id(), t.titulo(), t.materiaId(), t.materia(),
                t.prioridad(), enDias(7), EstadoTarea.PENDIENTE);

        assertTrue(repo.actualizar(reabierta));      // VENCIDA -> PENDIENTE

        assertEquals(reabierta, repo.buscarPorId(t.id()).get());
        assertEquals(2, contarAuditoria(t.id()));
    }

    @Test
    void actualizar_sinCambioDeEstado_noGeneraAuditoria() {
        Tarea t = tarea(materiaId, "EDA", 3, enDias(5));
        repo.insertar(t);
        Tarea soloTitulo = new Tarea(t.id(), "Otro título", t.materiaId(), t.materia(),
                t.prioridad(), t.fechaLimite(), EstadoTarea.PENDIENTE);

        assertTrue(repo.actualizar(soloTitulo));

        assertEquals(0, contarAuditoria(t.id()));
    }
}