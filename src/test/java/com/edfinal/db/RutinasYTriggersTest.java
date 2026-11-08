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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Procedimientos almacenados y triggers, ejercidos como los usará la aplicación (vía TareaRepository). */
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

    /** Pares [estado anterior, estado nuevo] de la auditoría de una tarea, en orden de registro. */
    private List<List<Integer>> transiciones(String tareaId) {
        return jdbc.query("""
                SELECT estado_anterior_id, estado_nuevo_id
                  FROM auditoria_tareas WHERE tarea_id = ?::uuid ORDER BY id
                """, (rs, i) -> List.of(rs.getInt(1), rs.getInt(2)), tareaId);
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

        assertThat(marcadas).isEqualTo(2);
        assertThat(estadoDe(expirada1.id())).isEqualTo(EstadoTarea.VENCIDA);
        assertThat(estadoDe(expirada2.id())).isEqualTo(EstadoTarea.VENCIDA);
        assertThat(estadoDe(vigente.id())).isEqualTo(EstadoTarea.PENDIENTE);
        assertThat(estadoDe(completada.id())).isEqualTo(EstadoTarea.COMPLETADA);
        assertThat(estadoDe(yaVencida.id())).isEqualTo(EstadoTarea.VENCIDA);

        // el trigger de auditoría registró exactamente las dos transiciones del procedimiento
        assertThat(transiciones(expirada1.id())).containsExactly(List.of(ID_PENDIENTE, ID_VENCIDA));
        assertThat(transiciones(expirada2.id())).containsExactly(List.of(ID_PENDIENTE, ID_VENCIDA));
        assertThat(transiciones(vigente.id())).isEmpty();
        // las otras dos solo tienen la transición que se les aplicó a mano, no una segunda
        assertThat(transiciones(completada.id())).containsExactly(List.of(ID_PENDIENTE, ID_COMPLETADA));
        assertThat(transiciones(yaVencida.id())).containsExactly(List.of(ID_PENDIENTE, ID_VENCIDA));
    }

    @Test
    void marcarVencidas_segundaLlamada_devuelveCero() {
        Tarea t = insertar(3, 5);
        forzarPlazoExpirado(t.id());

        assertThat(repo.marcarVencidas()).isEqualTo(1);
        assertThat(repo.marcarVencidas()).isZero();
    }

    @Test
    void marcarVencidas_sinTareas_devuelveCero() {
        assertThat(repo.marcarVencidas()).isZero();
    }

    @Test
    void marcarVencidas_plazoCortoInsertadoDesdeJava_seMarcaAlExpirar() throws InterruptedException {
        // Verifica que Java, el trigger de plazo y el procedimiento comparan contra la misma hora (zona).
        Tarea corta = tarea(materiaId, "EDA", 3, LocalDateTime.now().plusSeconds(3));
        repo.insertar(corta);

        assertThat(repo.marcarVencidas()).isZero();   // aún no expira
        Thread.sleep(4_000);
        assertThat(repo.marcarVencidas()).isEqualTo(1);
        assertThat(estadoDe(corta.id())).isEqualTo(EstadoTarea.VENCIDA);
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

        assertThat(repo.estadisticasPorMateria()).containsExactly(
                new EstadisticaMateria(vacia, "Algebra", 0, 0, 0, 0),
                new EstadisticaMateria(materiaId, "EDA", 4, 2, 1, 1));
    }

    // ---------- trg_tareas_validar_plazo ----------

    @Test
    void triggerPlazo_insertSqlConFechaPasada_rechaza_23514() {
        Throwable ex = catchThrowable(() -> jdbc.update("""
                INSERT INTO tareas (id, titulo, materia_id, prioridad_id, fecha_limite)
                VALUES (gen_random_uuid(), 'Pasada', ?, 3, LOCALTIMESTAMP - interval '1 day')
                """, materiaId));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23514");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tareas", Integer.class)).isZero();
    }

    @Test
    void triggerPlazo_noBloqueaUpdates_asiElUndoNoSeVeAfectado() {
        Tarea t = insertar(3, 5);

        forzarPlazoExpirado(t.id()); // UPDATE con fecha pasada: permitido

        assertThat(repo.buscarPorId(t.id()).orElseThrow().fechaLimite()).isBefore(LocalDateTime.now());
    }

    // ---------- trg_tareas_auditar_estado ----------

    @Test
    void triggerAuditoria_registraEstadoAnteriorNuevoYFecha() {
        Tarea t = insertar(3, 5);

        jdbc.update("UPDATE tareas SET estado_id = 2 WHERE id = ?::uuid", t.id());

        assertThat(transiciones(t.id())).containsExactly(List.of(ID_PENDIENTE, ID_COMPLETADA));
        LocalDateTime fecha = jdbc.queryForObject(
                "SELECT fecha_cambio FROM auditoria_tareas WHERE tarea_id = ?::uuid",
                LocalDateTime.class, t.id());
        assertThat(fecha).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void triggerAuditoria_registraCadaTransicion_enOrden() {
        Tarea t = insertar(3, 5);

        repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA);
        repo.actualizarEstado(t.id(), EstadoTarea.PENDIENTE);
        repo.actualizarEstado(t.id(), EstadoTarea.VENCIDA);

        assertThat(transiciones(t.id())).containsExactly(
                List.of(ID_PENDIENTE, ID_COMPLETADA),
                List.of(ID_COMPLETADA, ID_PENDIENTE),
                List.of(ID_PENDIENTE, ID_VENCIDA));
    }

    @Test
    void triggerAuditoria_noDisparaSiElEstadoNoCambia() {
        Tarea t = insertar(3, 5);

        jdbc.update("UPDATE tareas SET estado_id = estado_id WHERE id = ?::uuid", t.id());
        jdbc.update("UPDATE tareas SET titulo = 'Otro' WHERE id = ?::uuid", t.id());

        assertThat(transiciones(t.id())).isEmpty();
    }

    @Test
    void auditoria_tareaConHistorial_noSePuedeBorrar_fk_23503() {
        Tarea t = insertar(3, 5);
        repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA);

        Throwable ex = catchThrowable(
                () -> jdbc.update("DELETE FROM tareas WHERE id = ?::uuid", t.id()));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
    }
}