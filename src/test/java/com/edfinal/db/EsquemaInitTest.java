package com.edfinal.db;

import com.edfinal.support.PostgresTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Confirma que db/init/ se ejecutó completo en el contenedor de pruebas. */
class EsquemaInitTest extends PostgresTestBase {

    @Test
    void tablas_lasSeisExisten() {
        List<String> tablas = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                 WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                """, String.class);
        assertThat(tablas).containsExactlyInAnyOrder(
                "estado", "tipos_prioridad", "profesores", "materias", "tareas", "auditoria_tareas");
    }

    @Test
    void indices_existenLosDosDeTareas() {
        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
        assertThat(indices).contains("idx_tareas_fecha_limite", "idx_tareas_estado_id");
    }

    @Test
    void rutinas_procedimientoYFuncionConSuTipo() {
        String marcar = jdbc.queryForObject(
                "SELECT prokind FROM pg_proc WHERE proname = 'sp_marcar_vencidas'", String.class);
        String estadisticas = jdbc.queryForObject(
                "SELECT prokind FROM pg_proc WHERE proname = 'sp_estadisticas_entrega'", String.class);
        assertThat(marcar).isEqualTo("p");        // PROCEDURE
        assertThat(estadisticas).isEqualTo("f");  // FUNCTION
    }

    @Test
    void triggers_losDosDefinidos() {
        List<String> triggers = jdbc.queryForList(
                "SELECT tgname FROM pg_trigger WHERE NOT tgisinternal", String.class);
        assertThat(triggers).containsExactlyInAnyOrder(
                "trg_tareas_validar_plazo", "trg_tareas_auditar_estado");
    }

    @Test
    void catalogoEstados_idsDelContrato() {
        List<String> estados = jdbc.queryForList("SELECT nombre FROM estado ORDER BY id", String.class);
        assertThat(estados).containsExactly("PENDIENTE", "COMPLETADA", "VENCIDA");
    }

    @Test
    void catalogoPrioridades_idEsElNivel_yConservaTilde() {
        List<Integer> ids = jdbc.queryForList("SELECT id FROM tipos_prioridad ORDER BY id", Integer.class);
        String critica = jdbc.queryForObject("SELECT nombre FROM tipos_prioridad WHERE id = 5", String.class);
        assertThat(ids).containsExactly(1, 2, 3, 4, 5);
        assertThat(critica).isEqualTo("Crítica"); // verifica UTF-8 de punta a punta
    }

    @Test
    void zonaHoraria_esBogota() {
        String tz = jdbc.queryForObject("SHOW timezone", String.class);
        assertThat(tz).isEqualTo("America/Bogota");
    }
}