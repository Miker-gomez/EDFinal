package com.edfinal.repository;

import com.edfinal.domain.Materia;
import com.edfinal.domain.Profesor;
import com.edfinal.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class MateriaRepositoryTest extends PostgresTestBase {

    private final ProfesorRepository profesores = new ProfesorRepository(jdbc);
    private final MateriaRepository repo = new MateriaRepository(jdbc);

    @Test
    void insertar_devuelveMateriaConNombreDelProfesor() {
        Profesor p = profesores.insertar("Ana Gómez", "ana@u.edu");

        Materia m = repo.insertar("Estructuras de Datos", p.id());

        assertThat(m.id()).isPositive();
        assertThat(m.nombre()).isEqualTo("Estructuras de Datos");
        assertThat(m.profesorId()).isEqualTo(p.id());
        assertThat(m.profesor()).isEqualTo("Ana Gómez");
        assertThat(repo.buscarPorId(m.id())).contains(m);
    }

    @Test
    void insertar_profesorInexistente_fk_23503() {
        Throwable ex = catchThrowable(() -> repo.insertar("EDA", 99999));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
        assertThat(repo.listar()).isEmpty();
    }

    @Test
    void insertar_parNombreProfesorRepetido_unique_23505() {
        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("EDA", p.id());

        Throwable ex = catchThrowable(() -> repo.insertar("EDA", p.id()));

        assertThat(ex).isInstanceOf(DuplicateKeyException.class);
        assertThat(sqlState(ex)).isEqualTo("23505");
    }

    @Test
    void insertar_mismoNombreConOtroProfesor_esPermitido() {
        Profesor p1 = profesores.insertar("Ana", "ana@u.edu");
        Profesor p2 = profesores.insertar("Luis", "luis@u.edu");

        repo.insertar("EDA", p1.id());
        repo.insertar("EDA", p2.id());

        assertThat(repo.listar()).hasSize(2);
    }

    @Test
    void listar_ordenaPorNombre() {
        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("Física", p.id());
        repo.insertar("Cálculo", p.id());

        assertThat(repo.listar()).extracting(Materia::nombre).containsExactly("Cálculo", "Física");
    }

    @Test
    void buscarPorId_inexistente_devuelveVacio() {
        assertThat(repo.buscarPorId(99999)).isEmpty();
    }

    @Test
    void actualizar_cambiaNombreYProfesor() {
        Profesor p1 = profesores.insertar("Ana", "ana@u.edu");
        Profesor p2 = profesores.insertar("Luis", "luis@u.edu");
        Materia m = repo.insertar("EDA", p1.id());

        assertThat(repo.actualizar(m.id(), "EDA II", p2.id())).isTrue();

        assertThat(repo.buscarPorId(m.id()))
                .contains(new Materia(m.id(), "EDA II", p2.id(), "Luis"));
    }

    @Test
    void actualizar_inexistente_devuelveFalse() {
        Profesor p = profesores.insertar("Ana", "ana@u.edu");

        assertThat(repo.actualizar(99999, "X", p.id())).isFalse();
    }

    @Test
    void actualizar_profesorInexistente_fk_23503() {
        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        Materia m = repo.insertar("EDA", p.id());

        Throwable ex = catchThrowable(() -> repo.actualizar(m.id(), "EDA", 99999));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
        assertThat(repo.buscarPorId(m.id())).contains(m);
    }

    @Test
    void actualizar_aParExistente_unique_23505() {
        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("A", p.id());
        Materia b = repo.insertar("B", p.id());

        Throwable ex = catchThrowable(() -> repo.actualizar(b.id(), "A", p.id()));

        assertThat(ex).isInstanceOf(DuplicateKeyException.class);
        assertThat(sqlState(ex)).isEqualTo("23505");
    }

    @Test
    void eliminar_existente_devuelveTrueYDesaparece() {
        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        Materia m = repo.insertar("EDA", p.id());

        assertThat(repo.eliminar(m.id())).isTrue();

        assertThat(repo.buscarPorId(m.id())).isEmpty();
    }

    @Test
    void eliminar_inexistente_devuelveFalse() {
        assertThat(repo.eliminar(99999)).isFalse();
    }

    @Test
    void eliminar_conTareasAsociadas_restrict_23503_yPermanece() {
        int materiaId = crearMateria("EDA");
        jdbc.update("""
                INSERT INTO tareas (id, titulo, materia_id, prioridad_id, fecha_limite)
                VALUES (gen_random_uuid(), 'T', ?, 3, LOCALTIMESTAMP + interval '1 day')
                """, materiaId);

        Throwable ex = catchThrowable(() -> repo.eliminar(materiaId));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
        assertThat(repo.buscarPorId(materiaId)).isPresent();
    }

    @Test
    void profesorConMaterias_noSePuedeBorrar_restrict_23503() {
        Profesor p = profesores.insertar("Ana", "ana@u.edu");
        repo.insertar("EDA", p.id());

        Throwable ex = catchThrowable(
                () -> jdbc.update("DELETE FROM profesores WHERE id = ?", p.id()));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23503");
        assertThat(profesores.buscarPorId(p.id())).isPresent();
    }
}