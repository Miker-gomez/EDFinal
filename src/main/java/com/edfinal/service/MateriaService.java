package com.edfinal.service;

import com.edfinal.domain.Materia;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.ProfesorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

// Servicio para la gestión del catálogo de materias y asignaturas.
@Service
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final ProfesorRepository profesorRepository;

    public MateriaService(MateriaRepository materiaRepository, ProfesorRepository profesorRepository) {
        this.materiaRepository = materiaRepository;
        this.profesorRepository = profesorRepository;
    }

    public List<Materia> listar() {
        return materiaRepository.listar();
    }

    public Optional<Materia> buscarPorId(int id) {
        return materiaRepository.buscarPorId(id);
    }

    public Materia crear(String nombre, int profesorId) {
        Objects.requireNonNull(nombre, "El nombre no puede ser nulo");
        if (nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        profesorRepository.buscarPorId(profesorId)
                .orElseThrow(() -> new NoSuchElementException("El profesor con ID " + profesorId + " no existe"));

        return materiaRepository.insertar(nombre.trim(), profesorId);
    }

    public boolean actualizar(int id, String nombre, int profesorId) {
        Objects.requireNonNull(nombre, "El nombre no puede ser nulo");
        if (nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        profesorRepository.buscarPorId(profesorId)
                .orElseThrow(() -> new NoSuchElementException("El profesor con ID " + profesorId + " no existe"));

        return materiaRepository.actualizar(id, nombre.trim(), profesorId);
    }

    public boolean eliminar(int id) {
        return materiaRepository.eliminar(id);
    }
}
