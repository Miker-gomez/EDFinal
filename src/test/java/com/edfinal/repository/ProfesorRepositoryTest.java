package com.edfinal.repository;

import com.edfinal.domain.Profesor;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfesorRepositoryTest extends PostgresTestBase {

    private final ProfesorRepository repo = new ProfesorRepository(jdbc);

    @Test
    @DisplayName("Insertar devuelve el id generado y persiste")
    void insertar_devuelveIdGeneradoYPersiste() {

        Profesor p = repo.insertar("Ana Gómez", "ana@u.edu");

        assertTrue(p.id() > 0);
        assertEquals(p, repo.buscarPorId(p.id()).get());

    }

    @Test
    @DisplayName("Buscar un id inexistente devuelve vacío")
    void buscarPorId_inexistente_devuelveVacio() {

        assertTrue(repo.buscarPorId(99999).isEmpty());

    }

    @Test
    @DisplayName("Listar ordena los profesores por nombre")
    void listar_ordenaPorNombre() {

        repo.insertar("Zoe", "z@u.edu");
        repo.insertar("Ana", "a@u.edu");

        List<Profesor> lista = repo.listar();
        assertEquals("Ana", lista.get(0).nombre());
        assertEquals("Zoe", lista.get(1).nombre());

    }

    @Test
    @DisplayName("Insertar un correo duplicado falla por UNIQUE (23505)")
    void insertar_correoDuplicado_unique_23505() {

        repo.insertar("Ana", "ana@u.edu");

        Throwable ex = capturar(() -> repo.insertar("Otra", "ana@u.edu"));

        assertTrue(ex instanceof DuplicateKeyException);
        assertEquals("23505", sqlState(ex));
        assertEquals(1, repo.listar().size());

    }

    @Test
    @DisplayName("Insertar un correo sin arroba falla por CHECK (23514)")
    void insertar_correoSinArroba_check_23514() {

        Throwable ex = capturar(() -> repo.insertar("Ana", "sin-arroba"));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23514", sqlState(ex));
        assertTrue(repo.listar().isEmpty());

    }

    @Test
    @DisplayName("Insertar un nombre nulo falla por NOT NULL (23502)")
    void insertar_nombreNulo_notNull_23502() {

        Throwable ex = capturar(() -> repo.insertar(null, "ana@u.edu"));

        assertTrue(ex instanceof DataIntegrityViolationException);
        assertEquals("23502", sqlState(ex));

    }
}