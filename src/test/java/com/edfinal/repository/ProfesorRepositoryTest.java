package com.edfinal.repository;

import com.edfinal.domain.Profesor;
import com.edfinal.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ProfesorRepositoryTest extends PostgresTestBase {

    private final ProfesorRepository repo = new ProfesorRepository(jdbc);

    @Test
    void insertar_devuelveIdGeneradoYPersiste() {
        Profesor p = repo.insertar("Ana Gómez", "ana@u.edu");

        assertThat(p.id()).isPositive();
        assertThat(repo.buscarPorId(p.id())).contains(p);
    }

    @Test
    void buscarPorId_inexistente_devuelveVacio() {
        assertThat(repo.buscarPorId(99999)).isEmpty();
    }

    @Test
    void listar_ordenaPorNombre() {
        repo.insertar("Zoe", "z@u.edu");
        repo.insertar("Ana", "a@u.edu");

        assertThat(repo.listar()).extracting(Profesor::nombre).containsExactly("Ana", "Zoe");
    }

    @Test
    void insertar_correoDuplicado_unique_23505() {
        repo.insertar("Ana", "ana@u.edu");

        Throwable ex = catchThrowable(() -> repo.insertar("Otra", "ana@u.edu"));

        assertThat(ex).isInstanceOf(DuplicateKeyException.class);
        assertThat(sqlState(ex)).isEqualTo("23505");
        assertThat(repo.listar()).hasSize(1);
    }

    @Test
    void insertar_correoSinArroba_check_23514() {
        Throwable ex = catchThrowable(() -> repo.insertar("Ana", "sin-arroba"));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23514");
        assertThat(repo.listar()).isEmpty();
    }

    @Test
    void insertar_nombreNulo_notNull_23502() {
        Throwable ex = catchThrowable(() -> repo.insertar(null, "ana@u.edu"));

        assertThat(ex).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sqlState(ex)).isEqualTo("23502");
    }
}