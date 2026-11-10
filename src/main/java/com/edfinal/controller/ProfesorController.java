package com.edfinal.controller;

import com.edfinal.domain.Profesor;
import com.edfinal.dto.ProfesorRequest;
import com.edfinal.service.ProfesorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profesores")
public class ProfesorController {

    private final ProfesorService profesorService;

    public ProfesorController(ProfesorService profesorService) {

        this.profesorService = profesorService;

    }

    @GetMapping
    public List<Profesor> listar() {

        return profesorService.listar();

    }

    @PostMapping
    public ResponseEntity<Profesor> crear(@Valid @RequestBody ProfesorRequest request) {

        Profesor creado = profesorService.crear(request.nombre(), request.correo());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);

    }

}
