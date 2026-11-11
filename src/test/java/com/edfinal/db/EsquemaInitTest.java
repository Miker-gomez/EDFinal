package com.edfinal.db;

import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Confirma que db/init/ se ejecutó completo en el contenedor de pruebas.
class EsquemaInitTest extends PostgresTestBase {

    @Test
    @DisplayName("Las seis tablas del modelo existen en el esquema")
    void tablas_lasSeisExisten() {

        List<String> tablas = jdbc.queryForList("SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'", String.class);
        assertTrue(tablas.containsAll(List.of("estado", "tipos_prioridad", "profesores", "materias", "tareas", "auditoria_tareas")));
        assertEquals(List.of("estado", "tipos_prioridad", "profesores", "materias", "tareas", "auditoria_tareas").size(), tablas.size());

    }

    @Test
    @DisplayName("Existen los índices de fecha límite y de estado sobre tareas")
    void indices_existenLosDosDeTareas() {

        List<String> indices = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);
        assertTrue(indices.contains("idx_tareas_fecha_limite"));
        assertTrue(indices.contains("idx_tareas_estado_id"));

    }

    @Test
    @DisplayName("sp_marcar_vencidas es procedimiento y sp_estadisticas_entrega es función")
    void rutinas_procedimientoYFuncionConSuTipo() {

        String marcar = jdbc.queryForObject(
                "SELECT prokind FROM pg_proc WHERE proname = 'sp_marcar_vencidas'", String.class);
        String estadisticas = jdbc.queryForObject(
                "SELECT prokind FROM pg_proc WHERE proname = 'sp_estadisticas_entrega'", String.class);
        assertEquals("p", marcar);        // PROCEDURE
        assertEquals("f", estadisticas);  // FUNCTION

    }

    @Test
    @DisplayName("Están definidos el trigger de plazo y el de auditoría")
    void triggers_losDosDefinidos() {

        List<String> triggers = jdbc.queryForList(
                "SELECT tgname FROM pg_trigger WHERE NOT tgisinternal", String.class);
        assertTrue(triggers.containsAll(List.of("trg_tareas_validar_plazo", "trg_tareas_auditar_estado")));
        assertEquals(List.of("trg_tareas_validar_plazo", "trg_tareas_auditar_estado").size(), triggers.size());

    }

    @Test
    @DisplayName("El catálogo de estados usa los ids del contrato")
    void catalogoEstados_idsDelContrato() {

        List<String> estados = jdbc.queryForList("SELECT nombre FROM estado ORDER BY id", String.class);
        assertEquals(List.of("PENDIENTE", "COMPLETADA", "VENCIDA"), estados);

    }

    @Test
    @DisplayName("El id de la prioridad es su nivel y «Crítica» conserva la tilde")
    void catalogoPrioridades_idEsElNivel_yConservaTilde() {

        List<Integer> ids = jdbc.queryForList("SELECT id FROM tipos_prioridad ORDER BY id", Integer.class);
        String critica = jdbc.queryForObject("SELECT nombre FROM tipos_prioridad WHERE id = 5", String.class);
        assertEquals(List.of(1, 2, 3, 4, 5), ids);
        assertEquals("Crítica", critica); // verifica UTF-8 de punta a punta

    }

    @Test
    @DisplayName("La base usa la zona horaria America/Bogota")
    void zonaHoraria_esBogota() {

        String tz = jdbc.queryForObject("SHOW timezone", String.class);
        assertEquals("America/Bogota", tz);

    }
}