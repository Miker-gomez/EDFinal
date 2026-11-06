package com.edfinal.repository;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TareaRepositoryTest {

    @TempDir
    Path tmp;

    private SingleConnectionDataSource ds;
    private TareaRepository repo;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        ds = new SingleConnectionDataSource();
        ds.setDriverClassName("org.sqlite.JDBC");
        ds.setUrl("jdbc:sqlite:" + tmp.resolve("test.db"));
        ds.setSuppressClose(true);

        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);

        jdbc = new JdbcTemplate(ds);
        repo = new TareaRepository(jdbc);
    }

    @AfterEach
    void tearDown() {
        ds.destroy(); // cierra la conexión real y libera el archivo
    }

    private Tarea nueva(int prioridad) {
        return new Tarea(UUID.randomUUID().toString(), "EDA", "Taller", prioridad,
                LocalDateTime.of(2026, 11, 20, 8, 0), EstadoTarea.PENDIENTE);
    }

    @Test
    void insertarYBuscarPorId_roundTrip() {
        Tarea t = nueva(3);
        repo.insertar(t);
        assertThat(repo.buscarPorId(t.id())).contains(t);
    }

    @Test
    void buscarPorId_inexistente_devuelveVacio() {
        assertThat(repo.buscarPorId("nope")).isEmpty();
    }

    @Test
    void listarPendientes_excluyeCompletadas() {
        Tarea a = nueva(5), b = nueva(2);
        repo.insertar(a);
        repo.insertar(b);
        repo.actualizarEstado(b.id(), EstadoTarea.COMPLETADA);

        assertThat(repo.listarPendientes()).containsExactly(a);
        assertThat(repo.listarTodas()).hasSize(2);
    }

    @Test
    void actualizarEstado_idInexistente_devuelveFalse() {
        assertThat(repo.actualizarEstado("nope", EstadoTarea.COMPLETADA)).isFalse();
    }

    @Test
    void actualizarEstado_restauraAPendiente() {
        Tarea t = nueva(4);
        repo.insertar(t);
        repo.actualizarEstado(t.id(), EstadoTarea.COMPLETADA);
        repo.actualizarEstado(t.id(), EstadoTarea.PENDIENTE);
        assertThat(repo.buscarPorId(t.id())).contains(t);
    }

    @Test
    void check_prioridadFueraDeRango_lanzaExcepcion() {
        Tarea mala = new Tarea(UUID.randomUUID().toString(), "EDA", "X", 6,
                LocalDateTime.now(), EstadoTarea.PENDIENTE);
        assertThatThrownBy(() -> repo.insertar(mala)).isInstanceOf(Exception.class);
    }
}