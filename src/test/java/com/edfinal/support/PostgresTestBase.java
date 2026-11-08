package com.edfinal.support;

import com.edfinal.domain.EstadoTarea;
import com.edfinal.domain.Tarea;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Base de las pruebas de integración: un único contenedor PostgreSQL 16 por ejecución de la suite
 * (Ryuk lo elimina al terminar la JVM) con los mismos scripts de db/init/ que usa Docker Compose.
 * Cada test arranca con las tablas de datos vacías; los catálogos (estado, tipos_prioridad) se conservan.
 * Requiere Docker en ejecución y que el directorio de trabajo sea la raíz del proyecto (Gradle lo hace).
 */
public abstract class PostgresTestBase {

    private static final List<String> SCRIPTS = List.of("01_schema.sql", "02_seed.sql", "03_routines.sql");

    private static final PostgreSQLContainer<?> PG = crearContenedor();

    protected static final JdbcTemplate jdbc;

    static {
        PG.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> crearContenedor() {
        PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16")
                .withDatabaseName("student_hub_test")
                .withUsername("test")
                .withPassword("test")
                .withEnv("TZ", "America/Bogota")
                .withCommand("postgres", "-c", "fsync=off",
                        "-c", "timezone=America/Bogota", "-c", "log_timezone=America/Bogota");
        for (String script : SCRIPTS) {
            pg.withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("db", "init", script)),
                    "/docker-entrypoint-initdb.d/" + script);
        }
        return pg;
    }

    @BeforeEach
    protected void limpiarTablas() {
        // CASCADE desde profesores alcanza materias, tareas y auditoría; no toca los catálogos.
        jdbc.execute("TRUNCATE tareas, auditoria_tareas, materias, profesores RESTART IDENTITY CASCADE");
    }

    // ---------- helpers compartidos ----------

    /** SQLState de la primera SQLException en la cadena de causas (null si no hay). */
    protected static String sqlState(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException se) {
                return se.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }

    /** Ahora + n días, sin fracciones de segundo (la BD no devolvería los nanosegundos). */
    protected static LocalDateTime enDias(int dias) {
        return LocalDateTime.now().plusDays(dias).truncatedTo(ChronoUnit.SECONDS);
    }

    /** Crea un profesor nuevo (correo único) y una materia con ese nombre; devuelve el id de la materia. */
    protected static int crearMateria(String nombre) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Integer profesorId = jdbc.queryForObject(
                "INSERT INTO profesores (nombre, correo) VALUES (?, ?) RETURNING id",
                Integer.class, "Prof " + uid, uid + "@test.com");
        return jdbc.queryForObject(
                "INSERT INTO materias (nombre, profesor_id) VALUES (?, ?) RETURNING id",
                Integer.class, nombre, profesorId);
    }

    /** Tarea PENDIENTE con UUID nuevo. El nombre de la materia es solo de dominio: no se persiste. */
    protected static Tarea tarea(int materiaId, String materia, int prioridad, LocalDateTime fecha) {
        return new Tarea(UUID.randomUUID().toString(), "Taller", materiaId, materia,
                prioridad, fecha, EstadoTarea.PENDIENTE);
    }

    /** UPDATE directo que deja la fecha en el pasado (el trigger de plazo solo actúa en INSERT). */
    protected static void forzarPlazoExpirado(String tareaId) {
        jdbc.update("UPDATE tareas SET fecha_limite = LOCALTIMESTAMP - interval '1 hour' WHERE id = ?::uuid",
                tareaId);
    }

    protected static int contarAuditoria(String tareaId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria_tareas WHERE tarea_id = ?::uuid", Integer.class, tareaId);
        return n == null ? 0 : n;
    }
}