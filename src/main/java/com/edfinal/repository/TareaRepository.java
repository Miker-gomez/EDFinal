package com.edfinal.repository;

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

    private static final RowMapper<Tarea> MAPPER = (rs, i) -> new Tarea(
            rs.getString("id"),
            rs.getString("materia"),
            rs.getString("titulo"),
            rs.getInt("prioridad"),
            LocalDateTime.parse(rs.getString("fecha_limite")),
            EstadoTarea.valueOf(rs.getString("estado"))
    );

    private final JdbcTemplate jdbc;

    public TareaRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertar(Tarea t) {
        jdbc.update("""
                INSERT INTO tareas (id, materia, titulo, prioridad, fecha_limite, estado)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                t.id(), t.materia(), t.titulo(), t.prioridad(),
                t.fechaLimite().toString(), t.estado().name());
    }

    public List<Tarea> listarPendientes() {
        return jdbc.query("SELECT * FROM tareas WHERE estado = 'PENDIENTE'", MAPPER);
    }

    public List<Tarea> listarTodas() {
        return jdbc.query("SELECT * FROM tareas", MAPPER);
    }

    public Optional<Tarea> buscarPorId(String id) {
        return jdbc.query("SELECT * FROM tareas WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    /** @return true si se actualizó una fila; false si el ID no existe. */
    public boolean actualizarEstado(String id, EstadoTarea estado) {
        return jdbc.update("UPDATE tareas SET estado = ? WHERE id = ?",
                estado.name(), id) == 1;
    }
}