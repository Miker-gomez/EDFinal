package com.edfinal.service;

import com.edfinal.domain.Materia;
import com.edfinal.domain.Profesor;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.ProfesorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoServicesTest {

    @Mock
    private MateriaRepository materiaRepository;

    @Mock
    private ProfesorRepository profesorRepository;

    @InjectMocks
    private MateriaService materiaService;

    @InjectMocks
    private ProfesorService profesorService;

    @Test
    @DisplayName("ProfesorService: crear profesor válido")
    void crearProfesor_valido() {
        Profesor esperado = new Profesor(1, "Alan Turing", "alan@universidad.edu");
        when(profesorRepository.insertar("Alan Turing", "alan@universidad.edu")).thenReturn(esperado);

        Profesor creado = profesorService.crear("Alan Turing", "alan@universidad.edu");

        assertThat(creado).isEqualTo(esperado);
        verify(profesorRepository).insertar("Alan Turing", "alan@universidad.edu");
    }

    @Test
    @DisplayName("ProfesorService: rechazar correo sin arroba")
    void crearProfesor_correoInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> profesorService.crear("Ada Lovelace", "correo-invalido"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("MateriaService: crear materia con profesor existente")
    void crearMateria_valida() {
        Profesor prof = new Profesor(5, "Donald Knuth", "knuth@stanford.edu");
        Materia esperada = new Materia(10, "Algoritmos", 5, "Donald Knuth");

        when(profesorRepository.buscarPorId(5)).thenReturn(Optional.of(prof));
        when(materiaRepository.insertar("Algoritmos", 5)).thenReturn(esperada);

        Materia creada = materiaService.crear("Algoritmos", 5);

        assertThat(creada).isEqualTo(esperada);
    }

    @Test
    @DisplayName("MateriaService: crear materia con profesor inexistente lanza NoSuchElementException")
    void crearMateria_profesorInexistente_lanzaExcepcion() {
        when(profesorRepository.buscarPorId(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materiaService.crear("Bases de Datos", 99))
                .isInstanceOf(NoSuchElementException.class);
    }
}
