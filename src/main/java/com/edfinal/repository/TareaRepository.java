package com.edfinal.repository;

import com.edfinal.domain.EstadisticaMateria;
import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class TareaRepository {

    // prioridad_id == nivel 1..5 (5.10), por eso no se une tipos_prioridad.
    private static final String SELECT_BASE = """
            SELECT t.id, t.titulo, t.materia_id, m.nombre AS materia,
                   t.prioridad_id AS prioridad, t.fecha_limite, e.nombre AS estado
              FROM tareas t
              JOIN materias m ON m.id = t.materia_id
              JOIN estado   e ON e.id = t.estado_id
            """;

    private static final RowMapper<Tarea> MAPPER = (rs, i) -> new Tarea(
            rs.getString("id"),
            rs.getString("titulo"),
            rs.getInt("materia_id"),
            rs.getString("materia"),
            rs.getInt("prioridad"),
            rs.getObject("fecha_limite", LocalDateTime.class),
            EstadoTarea.valueOf(rs.getString("estado"))
    );

    private static final RowMapper<EstadisticaMateria> STATS_MAPPER = (rs, i) -> new EstadisticaMateria(
            rs.getInt("materia_id"),
            rs.getString("materia"),
            rs.getLong("total"),
            rs.getLong("pendientes"),
            rs.getLong("completadas"),
            rs.getLong("vencidas")
    );

    private final JdbcTemplate jdbc;

    public TareaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Usa t.materiaId() y t.prioridad(); t.materia() (nombre) no se persiste.
     * Fecha pasada => trigger de plazo (23514). Materia inexistente => 23503.
     */
    public void insertar(Tarea t) {
        jdbc.update("""
                INSERT INTO tareas (id, titulo, materia_id, estado_id, prioridad_id, fecha_limite)
                VALUES (?::uuid, ?, ?, (SELECT id FROM estado WHERE nombre = ?), ?, ?)
                """,
                t.id(), t.titulo(), t.materiaId(), t.estado().name(), t.prioridad(), t.fechaLimite());
    }

    /** PENDIENTE + VENCIDA: lo que vive en heap y HashMap. */
    public List<Tarea> listarActivas() {
        return jdbc.query(SELECT_BASE + " WHERE e.nombre IN ('PENDIENTE', 'VENCIDA')", MAPPER);
    }

    /** Cualquier estado (también COMPLETADA): deshacer relee la fila desde aquí. */
    public Optional<Tarea> buscarPorId(String id) {
        return jdbc.query(SELECT_BASE + " WHERE t.id = ?::uuid", MAPPER, id)
                .stream().findFirst();
    }

    /** @return true si se actualizó una fila; false si el ID no existe. */
    public boolean actualizarEstado(String id, EstadoTarea estado) {
        return jdbc.update("""
                UPDATE tareas
                   SET estado_id = (SELECT id FROM estado WHERE nombre = ?)
                 WHERE id = ?::uuid
                """, estado.name(), id) == 1;
    }

    /**
     * Edición (F09): persiste titulo, materia_id, prioridad, fecha_limite y estado.
     * El servicio decide el estado (VENCIDA + fecha nueva => PENDIENTE); aquí solo se escribe.
     * El trigger de auditoría solo registra si el estado cambió de verdad.
     * @return true si se actualizó una fila; false si el ID no existe.
     */
    public boolean actualizar(Tarea t) {
        return jdbc.update("""
                UPDATE tareas
                   SET titulo       = ?,
                       materia_id   = ?,
                       prioridad_id = ?,
                       fecha_limite = ?,
                       estado_id    = (SELECT id FROM estado WHERE nombre = ?)
                 WHERE id = ?::uuid
                """,
                t.titulo(), t.materiaId(), t.prioridad(), t.fechaLimite(), t.estado().name(), t.id()) == 1;
    }

    /** CALL sp_marcar_vencidas: el INOUT devuelve la cantidad marcada. */
    public int marcarVencidas() {
        Integer marcadas = jdbc.queryForObject("CALL sp_marcar_vencidas(0)", Integer.class);
        return marcadas == null ? 0 : marcadas;
    }

    public List<EstadisticaMateria> estadisticasPorMateria() {
        return jdbc.query("""
                SELECT materia_id, materia, total, pendientes, completadas, vencidas
                  FROM sp_estadisticas_entrega()
                """, STATS_MAPPER);
    }
}