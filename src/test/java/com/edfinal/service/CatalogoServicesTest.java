package com.edfinal.service;

import com.edfinal.domain.Materia;
import com.edfinal.domain.Profesor;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.ProfesorRepository;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogoServicesTest extends PostgresTestBase {

    private ProfesorRepository profesorRepository;
    private ProfesorService profesorService;
    private MateriaService materiaService;

    @BeforeEach
    void setUp() {

        // Arrange
        profesorRepository = new ProfesorRepository(jdbc);
        profesorService = new ProfesorService(profesorRepository);
        materiaService = new MateriaService(new MateriaRepository(jdbc), profesorRepository);

    }

    @Test
    @DisplayName("ProfesorService: crear profesor válido")
    void crearProfesor_valido() {

        // Act
        Profesor creado = profesorService.crear("Alan Turing", "alan@universidad.edu");

        // Assert
        assertEquals("Alan Turing", creado.nombre());
        assertEquals("alan@universidad.edu", creado.correo());
        assertEquals(creado, profesorRepository.buscarPorId(creado.id()).get());

    }

    @Test
    @DisplayName("ProfesorService: rechaza un correo sin arroba")
    void crearProfesor_correoInvalido_lanzaExcepcion() {

        // Act
        Throwable ex = capturar(() -> profesorService.crear("Ada Lovelace", "correo-invalido"));

        // Assert
        assertTrue(ex instanceof IllegalArgumentException);

    }

    @Test
    @DisplayName("MateriaService: crear materia con profesor existente")
    void crearMateria_valida() {

        // Arrange
        Profesor prof = profesorService.crear("Donald Knuth", "knuth@stanford.edu");

        // Act
        Materia creada = materiaService.crear("Algoritmos", prof.id());

        // Assert
        assertEquals("Algoritmos", creada.nombre());
        assertEquals(prof.id(), creada.profesorId());
        assertEquals("Donald Knuth", creada.profesor());

    }

    @Test
    @DisplayName("MateriaService: profesor inexistente lanza NoSuchElementException")
    void crearMateria_profesorInexistente_lanzaExcepcion() {

        // Act
        Throwable ex = capturar(() -> materiaService.crear("Bases de Datos", 99999));

        // Assert
        assertTrue(ex instanceof java.util.NoSuchElementException);

    }

}
