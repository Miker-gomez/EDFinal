package com.edfinal.controller;

import com.edfinal.domain.Materia;
import com.edfinal.dto.MateriaRequest;
import com.edfinal.exception.MateriaNoEncontradaException;
import com.edfinal.service.MateriaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/materias")
public class MateriaController {

    private final MateriaService materiaService;

    public MateriaController(MateriaService materiaService) {

        this.materiaService = materiaService;

    }

    @GetMapping
    public List<Materia> listar() {

        return materiaService.listar();

    }

    @GetMapping("/{id}")
    public Materia consultar(@PathVariable int id) {

        return materiaService.buscarPorId(id)
                .orElseThrow(() -> new MateriaNoEncontradaException("Materia no encontrada"));

    }

    @PostMapping
    public ResponseEntity<Materia> crear(@Valid @RequestBody MateriaRequest request) {

        Materia creada = materiaService.crear(request.nombre(), request.profesorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);

    }

    @PutMapping("/{id}")
    public Materia editar(@PathVariable int id, @Valid @RequestBody MateriaRequest request) {

        return materiaService.actualizar(id, request.nombre(), request.profesorId());

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable int id) {

        materiaService.eliminar(id);
        return ResponseEntity.noContent().build();

    }

}
