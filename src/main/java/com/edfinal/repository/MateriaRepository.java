package com.edfinal.repository;

import com.edfinal.domain.Materia;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Repositorio de materias con CRUD
@Repository
public class MateriaRepository {

    private static final String SELECT_BASE = "SELECT m.id, m.nombre, m.profesor_id, p.nombre AS profesor " +
                    "FROM materias m " +
                    "JOIN profesores p ON p.id = m.profesor_id";

    private static final RowMapper<Materia> MAPPER = (rs, i) -> new Materia(
            rs.getInt("id"),
            rs.getString("nombre"),
            rs.getInt("profesor_id"),
            rs.getString("profesor")
    );

    private final JdbcTemplate jdbc;

    public MateriaRepository(JdbcTemplate jdbc) {

        this.jdbc = jdbc;

    }

    // Un solo INSERT con CTE para devolver también el nombre del profesor
    public Materia insertar(String nombre, int profesorId) {

        return jdbc.queryForObject("WITH nueva AS ( " +
                    "INSERT INTO materias (nombre, profesor_id) " +
                    "VALUES (?, ?) " +
                    "RETURNING id, nombre, profesor_id " +
                    ") " +
                    "SELECT n.id, n.nombre, n.profesor_id, p.nombre AS profesor " +
                    "FROM nueva n " +
                    "JOIN profesores p ON p.id = n.profesor_id", MAPPER, nombre, profesorId);

    }

    public List<Materia> listar() {

        return jdbc.query(SELECT_BASE + " ORDER BY m.nombre, m.id", MAPPER);

    }

    public Optional<Materia> buscarPorId(int id) {

        return jdbc.query(SELECT_BASE + " WHERE m.id = ?", MAPPER, id)
                .stream().findFirst();

    }

    // Devuelve true si se actualizó una fila; false si el id no existe
    public boolean actualizar(int id, String nombre, int profesorId) {

        return jdbc.update("UPDATE materias SET nombre = ?, profesor_id = ? WHERE id = ?",
                nombre, profesorId, id) == 1;

    }

    // Borra la materia junto con sus tareas ELIMINADAS y la auditoría de esas tareas en una sola sentencia
    // Si quedan tareas en otro estado la llave foránea rechaza todo (23503) y no se borra nada
    // Devuelve true si se eliminó una fila; false si el id no existe
    public boolean eliminar(int id) {

        return jdbc.update("WITH eliminadas AS ( " +
                    "SELECT t.id FROM tareas t " +
                    "JOIN estado e ON e.id = t.estado_id " +
                    "WHERE t.materia_id = ? AND e.nombre = 'ELIMINADA' " +
                    "), auditoria AS ( " +
                    "DELETE FROM auditoria_tareas WHERE tarea_id IN (SELECT id FROM eliminadas) " +
                    "), borradas AS ( " +
                    "DELETE FROM tareas WHERE id IN (SELECT id FROM eliminadas) " +
                    ") " +
                    "DELETE FROM materias WHERE id = ?", id, id) == 1;

    }
}