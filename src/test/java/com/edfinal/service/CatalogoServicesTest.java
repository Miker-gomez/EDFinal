package com.edfinal.service;

import com.edfinal.domain.Materia;
import com.edfinal.domain.Profesor;
import com.edfinal.domain.Tarea;
import com.edfinal.exception.MateriaNoEncontradaException;
import com.edfinal.exception.ProfesorNoEncontradoException;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.ProfesorRepository;
import com.edfinal.repository.TareaRepository;
import com.edfinal.support.PostgresTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.edfinal.support.Excepciones.capturar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogoServicesTest extends PostgresTestBase {

    private ProfesorRepository profesorRepository;
    private ProfesorService profesorService;
    private MateriaService materiaService;
    private TareaService tareaService;

    @BeforeEach
    void setUp() {

        // Arrange
        profesorRepository = new ProfesorRepository(jdbc);
        profesorService = new ProfesorService(profesorRepository);
        MateriaRepository materiaRepository = new MateriaRepository(jdbc);
        tareaService = new TareaService(new TareaRepository(jdbc), materiaRepository);
        materiaService = new MateriaService(materiaRepository, profesorRepository, tareaService);

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
    @DisplayName("MateriaService: profesor inexistente lanza ProfesorNoEncontradoException")
    void crearMateria_profesorInexistente_lanzaExcepcion() {

        // Act
        Throwable ex = capturar(() -> materiaService.crear("Bases de Datos", 99999));

        // Assert
        assertTrue(ex instanceof ProfesorNoEncontradoException);

    }

    @Test
    @DisplayName("MateriaService: editar el nombre recarga la materia de las tareas en el heap")
    void actualizarMateria_recargaEstructuras() {

        // Arrange
        Profesor prof = profesorService.crear("Edsger Dijkstra", "dijkstra@tue.nl");
        Materia materia = materiaService.crear("Grafos", prof.id());
        tareaService.inicializar();
        Tarea tarea = tareaService.crearTarea("Taller", materia.id(), 3, LocalDateTime.now().plusDays(2));

        // Act
        materiaService.actualizar(materia.id(), "Teoría de Grafos", prof.id());

        // Assert
        assertEquals("Teoría de Grafos", tareaService.buscarPorId(tarea.id()).get().materia());

    }

    @Test
    @DisplayName("MateriaService: actualizar y eliminar una materia inexistente lanzan MateriaNoEncontradaException")
    void materiaInexistente_lanzaExcepcion() {

        // Arrange
        Profesor prof = profesorService.crear("Barbara Liskov", "liskov@mit.edu");

        // Act
        Throwable alActualizar = capturar(() -> materiaService.actualizar(99999, "Nombre", prof.id()));
        Throwable alEliminar = capturar(() -> materiaService.eliminar(99999));

        // Assert
        assertTrue(alActualizar instanceof MateriaNoEncontradaException);
        assertTrue(alEliminar instanceof MateriaNoEncontradaException);

    }

}
