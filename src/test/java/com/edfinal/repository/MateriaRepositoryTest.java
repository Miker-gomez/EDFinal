package com.edfinal.repository;

import com.edfinal.domain.Materia;
import com.edfinal.domain.Profesor;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MateriaRepositoryTest extends PostgresTestBase {

    private final ProfesorRepository profesores = new ProfesorRepository(jdbc);
    private final MateriaRepository repo = new MateriaRepository(jdbc);

    @Test
    @DisplayName("Insertar devuelve la materia con el nombre del profesor")
    void insertar_devuelveMateriaConNombreDelProfesor() {

        Profesor p = profesores.insertar("Ana Gómez", "ana@u.edu");

        Materia m = repo.insertar("Estructuras de Datos", p.id());

        assertTrue(m.id() > 0);
        assertEquals("Estructuras de Datos", m.nombre());
        assertEquals(p.id(), m.profesorId());
        assertEquals("Ana Gómez", m.profesor());
        assertEquals(m, repo.buscarPorId(m.id()).get());

    }

    @Test
    @DisplayName("Insertar con un profesor inexistente falla por llave foránea (23503)")
    void insertar_profesorInexistente_fk_23503() {

        Throwable ex = capturar(() -> repo.insertar("EDA", 99999));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
        assertTrue(repo.listar().isEmpty());

    }

    @Test
    @DisplayName("Insertar un par nombre-profesor repetido falla por UNIQUE (23505)")
    void insertar_parNombreProfesorRepetido_unique_23505() {

        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("EDA", p.id());

        Throwable ex = capturar(() -> repo.insertar("EDA", p.id()));

        assertTrue(ex instanceof DuplicateKeyException);
        assertEquals("23505", sqlState(ex));

    }

    @Test
    @DisplayName("El mismo nombre de materia con otro profesor se permite")
    void insertar_mismoNombreConOtroProfesor_esPermitido() {

        Profesor p1 = profesores.insertar("Ana", "ana@u.edu");
        Profesor p2 = profesores.insertar("Luis", "luis@u.edu");

        repo.insertar("EDA", p1.id());
        repo.insertar("EDA", p2.id());

        assertEquals(2, repo.listar().size());

    }

    @Test
    @DisplayName("Listar ordena las materias por nombre")
    void listar_ordenaPorNombre() {

        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("Física", p.id());
        repo.insertar("Cálculo", p.id());

        List<Materia> lista = repo.listar();
        assertEquals("Cálculo", lista.get(0).nombre());
        assertEquals("Física", lista.get(1).nombre());

    }

    @Test
    @DisplayName("Buscar un id inexistente devuelve vacío")
    void buscarPorId_inexistente_devuelveVacio() {

        assertTrue(repo.buscarPorId(99999).isEmpty());

    }

    @Test
    @DisplayName("Actualizar cambia el nombre y el profesor")
    void actualizar_cambiaNombreYProfesor() {

        Profesor p1 = profesores.insertar("Ana", "ana@u.edu");
        Profesor p2 = profesores.insertar("Luis", "luis@u.edu");
        Materia m = repo.insertar("EDA", p1.id());

        assertTrue(repo.actualizar(m.id(), "EDA II", p2.id()));

        assertEquals(new Materia(m.id(), "EDA II", p2.id(), "Luis"), repo.buscarPorId(m.id()).get());

    }

    @Test
    @DisplayName("Actualizar un id inexistente devuelve false")
    void actualizar_inexistente_devuelveFalse() {

        Profesor p = profesores.insertar("Ana", "ana@u.edu");

        assertFalse(repo.actualizar(99999, "X", p.id()));

    }

    @Test
    @DisplayName("Actualizar con un profesor inexistente falla por llave foránea (23503)")
    void actualizar_profesorInexistente_fk_23503() {

        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        Materia m = repo.insertar("EDA", p.id());

        Throwable ex = capturar(() -> repo.actualizar(m.id(), "EDA", 99999));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
        assertEquals(m, repo.buscarPorId(m.id()).get());

    }

    @Test
    @DisplayName("Actualizar a un par existente falla por UNIQUE (23505)")
    void actualizar_aParExistente_unique_23505() {

        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("A", p.id());
        Materia b = repo.insertar("B", p.id());

        Throwable ex = capturar(() -> repo.actualizar(b.id(), "A", p.id()));

        assertTrue(ex instanceof DuplicateKeyException);
        assertEquals("23505", sqlState(ex));

    }

    @Test
    @DisplayName("Eliminar una materia existente devuelve true y desaparece")
    void eliminar_existente_devuelveTrueYDesaparece() {

        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        Materia m = repo.insertar("EDA", p.id());

        assertTrue(repo.eliminar(m.id()));

        assertTrue(repo.buscarPorId(m.id()).isEmpty());

    }

    @Test
    @DisplayName("Eliminar un id inexistente devuelve false")
    void eliminar_inexistente_devuelveFalse() {

        assertFalse(repo.eliminar(99999));

    }

    @Test
    @DisplayName("Eliminar una materia con tareas falla por RESTRICT y permanece (23503)")
    void eliminar_conTareasAsociadas_restrict_23503_yPermanece() {

        int materiaId = crearMateria("EDA");
        jdbc.update("INSERT INTO tareas (id, titulo, materia_id, prioridad_id, fecha_limite) " +
                    "VALUES (gen_random_uuid(), 'T', ?, 3, LOCALTIMESTAMP + interval '1 day')", materiaId);

        Throwable ex = capturar(() -> repo.eliminar(materiaId));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
        assertTrue(repo.buscarPorId(materiaId).isPresent());

    }

    @Test
    @DisplayName("Eliminar una materia con solo tareas ELIMINADAS borra la materia, esas tareas y su auditoría")
    void eliminar_soloTareasEliminadas_borraTodo() {

        // Arrange
        int materiaId = crearMateria("EDA");
        jdbc.update("INSERT INTO tareas (id, titulo, materia_id, prioridad_id, fecha_limite) " +
                    "VALUES (gen_random_uuid(), 'T', ?, 3, LOCALTIMESTAMP + interval '1 day')", materiaId);
        jdbc.update("UPDATE tareas SET estado_id = 4 WHERE materia_id = ?", materiaId);
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM auditoria_tareas", Integer.class));

        // Act
        boolean eliminada = repo.eliminar(materiaId);

        // Assert
        assertTrue(eliminada);
        assertTrue(repo.buscarPorId(materiaId).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tareas WHERE materia_id = ?", Integer.class, materiaId));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM auditoria_tareas", Integer.class));

    }

    @Test
    @DisplayName("Si la materia tiene una tarea activa además de las ELIMINADAS no se borra nada (23503)")
    void eliminar_tareaActivaYEliminada_noBorraNada() {

        // Arrange
        int materiaId = crearMateria("EDA");
        jdbc.update("INSERT INTO tareas (id, titulo, materia_id, prioridad_id, fecha_limite, estado_id) " +
                    "VALUES (gen_random_uuid(), 'Borrada', ?, 3, LOCALTIMESTAMP + interval '1 day', 4)", materiaId);
        jdbc.update("INSERT INTO tareas (id, titulo, materia_id, prioridad_id, fecha_limite) " +
                    "VALUES (gen_random_uuid(), 'Activa', ?, 3, LOCALTIMESTAMP + interval '1 day')", materiaId);

        // Act
        Throwable ex = capturar(() -> repo.eliminar(materiaId));

        // Assert
        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
        assertTrue(repo.buscarPorId(materiaId).isPresent());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM tareas WHERE materia_id = ?", Integer.class, materiaId));

    }

    @Test
    @DisplayName("Un profesor con materias no se puede borrar por RESTRICT (23503)")
    void profesorConMaterias_noSePuedeBorrar_restrict_23503() {

        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("EDA", p.id());

        Throwable ex = capturar(
                () -> jdbc.update("DELETE FROM profesores WHERE id = ?", p.id()));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23503", sqlState(ex));
        assertTrue(profesores.buscarPorId(p.id()).isPresent());

    }
}