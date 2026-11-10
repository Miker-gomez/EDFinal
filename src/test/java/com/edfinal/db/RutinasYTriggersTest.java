package com.edfinal.db;

import com.edfinal.domain.EstadisticaMateria;
import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import com.edfinal.repository.TareaRepository;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Procedimientos almacenados y triggers, ejercidos como los usará la aplicación (vía TareaRepository).
class RutinasYTriggersTest extends PostgresTestBase {

    private static final int ID_PENDIENTE = 1;
    private static final int ID_COMPLETADA = 2;
    private static final int ID_VENCIDA = 3;

    private final TareaRepository repo = new TareaRepository(jdbc);
    private int materiaId;

    @BeforeEach
    void crearMateriaBase() {

        materiaId = crearMateria("EDA");

    }

    private Tarea insertar(int prioridad, int dias) {

        Tarea t = tarea(materiaId, "EDA", prioridad, enDias(dias));
        repo.insertar(t);
        return t;

    }

    private EstadoTarea estadoDe(String id) {

        return repo.buscarPorId(id).orElseThrow().estado();

    }

    // Pares [estado anterior, estado nuevo] de la auditoría de una tarea, en orden de registro.
    private List<List<Integer>> transiciones(String tareaId) {

        return jdbc.query("SELECT estado_anterior_id, estado_nuevo_id " +
                    "FROM auditoria_tareas WHERE tarea_id = ?::uuid ORDER BY id", (rs, i) -> List.of(rs.getInt(1), rs.getInt(2)), tareaId);

    }

    // ---------- sp_marcar_vencidas (CALL con INOUT desde JdbcTemplate) ----------

    @Test
    void marcarVencidas_marcaSoloPendientesExpiradas_yDevuelveLaCantidad() {

        Tarea expirada1 = insertar(3, 5);
        Tarea expirada2 = insertar(4, 5);
        Tarea vigente = insertar(5, 5);
        Tarea completada = insertar(2, 5);
        Tarea yaVencida = insertar(1, 5);
        for (Tarea t : List.of(expirada1, expirada2, completada, yaVencida)) {

            forzarPlazoExpirado(t.id());

        }
        repo.actualizarEstado(completada.id(), EstadoTarea.COMPLETADA);
        repo.actualizarEstado(yaVencida.id(), EstadoTarea.VENCIDA);

        int marcadas = repo.marcarVencidas();

        assertEquals(2, marcadas);
        assertEquals(EstadoTarea.VENCIDA, estadoDe(expirada1.id()));
        assertEquals(EstadoTarea.VENCIDA, estadoDe(expirada2.id()));
        assertEquals(EstadoTarea.PENDIENTE, estadoDe(vigente.id()));
        assertEquals(EstadoTarea.COMPLETADA, estadoDe(completada.id()));
        assertEquals(EstadoTarea.VENCIDA, estadoDe(yaVencida.id()));

        // el trigger de auditoría registró exactamente las dos transiciones del procedimiento
        assertEquals(List.of(List.of(ID_PENDIENTE, ID_VENCIDA)), transiciones(expirada1.id()));
        assertEquals(List.of(List.of(ID_PENDIENTE, ID_VENCIDA)), transiciones(expirada2.id()));
        assertTrue(transiciones(vigente.id()).isEmpty());
        // las otras dos solo tienen la transición que se les aplicó a mano, no una segunda
        assertEquals(List.of(List.of(ID_PENDIENTE, ID_COMPLETADA)), transiciones(completada.id()));
        assertEquals(List.of(List.of(ID_PENDIENTE, ID_VENCIDA)), transiciones(yaVencida.id()));

    }

    @Test
    void marcarVencidas_segundaLlamada_devuelveCero() {

        Tarea t = insertar(3, 5);
        forzarPlazoExpirado(t.id());

        assertEquals(1, repo.marcarVencidas());
        assertEquals(0, repo.marcarVencidas());

    }

    @Test
    void marcarVencidas_sinTareas_devuelveCero() {

        assertEquals(0, repo.marcarVencidas());

    }

    @Test
    void marcarVencidas_plazoCortoInsertadoDesdeJava_seMarcaAlExpirar() throws InterruptedException {

        // Verifica que Java, el trigger de plazo y el procedimiento comparan contra la misma hora (zona).
        Tarea corta = tarea(materiaId, "EDA", 3, LocalDateTime.now().plusSeconds(3));
        repo.insertar(corta);

        assertEquals(0, repo.marcarVencidas());   // aún no expira
        Thread.sleep(4_000);
        assertEquals(1, repo.marcarVencidas());
        assertEquals(EstadoTarea.VENCIDA, estadoDe(corta.id()));

    }

    // ---------- sp_estadisticas_entrega ----------

    @Test
    void estadisticas_conteoPorMateriaYEstado_incluyeMateriasSinTareas() {

        int vacia = crearMateria("Algebra");
        insertar(3, 5);
        insertar(3, 6);
        Tarea completada = insertar(3, 7);
        Tarea vencida = insertar(3, 8);
        repo.actualizarEstado(completada.id(), EstadoTarea.COMPLETADA);
        repo.actualizarEstado(vencida.id(), EstadoTarea.VENCIDA);

        assertEquals(List.of(new EstadisticaMateria(vacia, "Algebra", 0, 0, 0, 0),
                new EstadisticaMateria(materiaId, "EDA", 4, 2, 1, 1)), repo.estadisticasPorMateria());

    }

    // ---------- trg_tareas_validar_plazo ----------

    @Test
    void triggerPlazo_insertSqlConFechaPasada_rechaza_23514() {

        Throwable ex = capturar(() -> jdbc.update("INSERT INTO tareas (id, titulo, materia_id, prioridad_id, fecha_limite) " +
                    "VALUES (gen_random_uuid(), 'Pasada', ?, 3, LOCALTIMESTAMP - interval '1 day')", materiaId));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23514", sqlState(ex));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tareas", Integer.class));

    }

    @Test
    void triggerPlazo_noBloqueaUpdates_asiElUndoNoSeVeAfectado() {

        Tarea t = insertar(3, 5);

        forzarPlazoExpirado(t.id()); // UPDATE con fecha pasada: permitido

        assertTrue(repo.buscarPorId(t.id()).orElseThrow().fechaLimite().isBefore(LocalDateTime.now()));

    }

    // ---------- trg_tareas_auditar_estado ----------

    @Test
    void triggerAuditoria_registraEstadoAnteriorNuevoYFecha() {

        Tarea t = insertar(3, 5);

        jdbc.update("UPDATE tareas SET estado_id = 2 WHERE id = ?::uuid", t.id());

        assertEquals(List.of(List.of(ID_PENDIENTE, ID_COMPLETADA)), transiciones(t.id()));
        LocalDateTime fecha = jdbc.queryForObject(
                "SELECT fecha_cambio FROM auditoria_tareas WHERE tarea_id = ?::uuid",
                LocalDateTime.class, t.id());
        assertTrue(fecha.isAfter(LocalDateTime.now().minusMinutes(1)));

    }

    @Test
    void triggerAuditoria_registraCadaTransicion_enOrden() {

        Tarea t = insertar(3, 5);

        repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA);
        repo.actualizarEstado(t.id(), EstadoTarea.PENDIENTE);
        repo.actualizarEstado(t.id(), EstadoTarea.VENCIDA);

        assertEquals(List.of(List.of(ID_PENDIENTE, ID_COMPLETADA),
                List.of(ID_COMPLETADA, ID_PENDIENTE),
                List.of(ID_PENDIENTE, ID_VENCIDA)), transiciones(t.id()));

    }

    @Test
    void triggerAuditoria_noDisparaSiElEstadoNoCambia() {

        Tarea t = insertar(3, 5);

        jdbc.update("UPDATE tareas SET estado_id = estado_id WHERE id = ?::uuid", t.id());
        jdbc.update("UPDATE tareas SET titulo = 'Otro' WHERE id = ?::uuid", t.id());

        assertTrue(transiciones(t.id()).isEmpty());

    }

    @Test
    void auditoria_tareaConHistorial_noSePuedeBorrar_fk_23503() {

        Tarea t = insertar(3, 5);
        repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA);

        Throwable ex = capturar(
                () -> jdbc.update("DELETE FROM tareas WHERE id = ?::uuid", t.id()));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));

    }
}