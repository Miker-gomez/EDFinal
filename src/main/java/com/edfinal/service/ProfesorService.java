package com.edfinal.service;

import com.edfinal.domain.Profesor;
import com.edfinal.repository.ProfesorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio para la gestión del catálogo de profesores.
 */
@Service
public class ProfesorService {

    private final ProfesorRepository profesorRepository;

    public ProfesorService(ProfesorRepository profesorRepository) {
        this.profesorRepository = profesorRepository;
    }

    public List<Profesor> listar() {
        return profesorRepository.listar();
    }

    public Optional<Profesor> buscarPorId(int id) {
        return profesorRepository.buscarPorId(id);
    }

    public Profesor crear(String nombre, String correo) {
        Objects.requireNonNull(nombre, "El nombre no puede ser nulo");
        Objects.requireNonNull(correo, "El correo no puede ser nulo");
        if (nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        if (!correo.contains("@")) {
            throw new IllegalArgumentException("El correo debe tener un formato válido con '@'");
        }
        return profesorRepository.insertar(nombre.trim(), correo.trim().toLowerCase());
    }
}
