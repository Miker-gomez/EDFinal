package com.edfinal.controller;

import com.edfinal.domain.Tarea;
import com.edfinal.dto.TareaRequest;
import com.edfinal.dto.VencidasResponse;
import com.edfinal.exception.TareaNoEncontradaException;
import com.edfinal.service.TareaService;
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
@RequestMapping("/api/tareas")
public class TareaController {

    private final TareaService tareaService;

    public TareaController(TareaService tareaService) {

        this.tareaService = tareaService;

    }

    @PostMapping
    public ResponseEntity<Tarea> crear(@Valid @RequestBody TareaRequest request) {

        Tarea creada = tareaService.crearTarea(request.titulo(), request.materiaId(), request.prioridad(), request.fechaLimite());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);

    }

    @PutMapping("/{id}")
    public Tarea editar(@PathVariable String id, @Valid @RequestBody TareaRequest request) {

        return tareaService.actualizarTarea(id, request.titulo(), request.materiaId(), request.prioridad(), request.fechaLimite());

    }

    @GetMapping
    public List<Tarea> listar() {

        return tareaService.listarActivas();

    }

    // 204 cuando no hay tareas activas
    @GetMapping("/critica")
    public ResponseEntity<Tarea> consultarCritica() {

        return tareaService.obtenerMasUrgente()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());

    }

    @GetMapping("/{id}")
    public Tarea buscar(@PathVariable String id) {

        return tareaService.buscarPorId(id)
                .orElseThrow(() -> new TareaNoEncontradaException("Tarea no encontrada"));

    }

    @PutMapping("/{id}/completar")
    public Tarea completar(@PathVariable String id) {

        return tareaService.completarTarea(id);

    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {

        tareaService.eliminarTarea(id);
        return ResponseEntity.noContent().build();

    }

    @PostMapping("/deshacer")
    public Tarea deshacer() {

        return tareaService.deshacer();

    }

    @PostMapping("/vencidas")
    public VencidasResponse marcarVencidas() {

        return new VencidasResponse(tareaService.sincronizarVencidas());

    }

}
