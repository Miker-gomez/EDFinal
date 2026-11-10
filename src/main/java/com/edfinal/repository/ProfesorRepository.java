package com.edfinal.repository;

import com.edfinal.domain.Profesor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProfesorRepository {

    private static final RowMapper<Profesor> MAPPER = (rs, i) -> new Profesor(
            rs.getInt("id"),
            rs.getString("nombre"),
            rs.getString("correo")
    );

    private final JdbcTemplate jdbc;

    public ProfesorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // El id es GENERATED ALWAYS: se obtiene con RETURNING
    // Un correo repetido lanza DuplicateKeyException (23505)
    public Profesor insertar(String nombre, String correo) {
        Integer id = jdbc.queryForObject("INSERT INTO profesores (nombre, correo) " +
                    "VALUES (?, ?) " +
                    "RETURNING id", Integer.class, nombre, correo);
        return new Profesor(id, nombre, correo);
    }

    public List<Profesor> listar() {
        return jdbc.query("SELECT id, nombre, correo FROM profesores ORDER BY nombre, id", MAPPER);
    }

    public Optional<Profesor> buscarPorId(int id) {
        return jdbc.query("SELECT id, nombre, correo FROM profesores WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }
}