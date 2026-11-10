package com.edfinal.service;

import com.edfinal.domain.Materia;
import com.edfinal.exception.MateriaNoEncontradaException;
import com.edfinal.exception.ProfesorNoEncontradoException;
import com.edfinal.repository.MateriaRepository;
import com.edfinal.repository.ProfesorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

// Servicio para la gestión del catálogo de materias y asignaturas.
@Service
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final ProfesorRepository profesorRepository;
    private final TareaService tareaService;

    public MateriaService(MateriaRepository materiaRepository, ProfesorRepository profesorRepository, TareaService tareaService) {

        this.materiaRepository = materiaRepository;
        this.profesorRepository = profesorRepository;
        this.tareaService = tareaService;

    }

    public List<Materia> listar() {

        return materiaRepository.listar();

    }

    public Optional<Materia> buscarPorId(int id) {

        return materiaRepository.buscarPorId(id);

    }

    public Materia crear(String nombre, int profesorId) {

        validarDatos(nombre, profesorId);
        return materiaRepository.insertar(nombre.trim(), profesorId);

    }

    // Editar el nombre cambia la materia que muestran las tareas, por eso se recargan las estructuras
    public Materia actualizar(int id, String nombre, int profesorId) {

        validarDatos(nombre, profesorId);
        if (!materiaRepository.actualizar(id, nombre.trim(), profesorId)) {
            throw new MateriaNoEncontradaException("Materia no encontrada");
        }
        tareaService.recargarDesdeBaseDeDatos();
        return materiaRepository.buscarPorId(id)
                .orElseThrow(() -> new MateriaNoEncontradaException("Materia no encontrada"));

    }

    // Una materia con tareas asociadas la rechaza la llave foránea (23503)
    public void eliminar(int id) {

        if (!materiaRepository.eliminar(id)) {
            throw new MateriaNoEncontradaException("Materia no encontrada");
        }

    }

    private void validarDatos(String nombre, int profesorId) {

        Objects.requireNonNull(nombre, "El nombre no puede ser nulo");
        if (nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre no puede estar vacío");
        }
        if (profesorRepository.buscarPorId(profesorId).isEmpty()) {
            throw new ProfesorNoEncontradoException("El profesor con ID " + profesorId + " no existe");
        }

    }
}
