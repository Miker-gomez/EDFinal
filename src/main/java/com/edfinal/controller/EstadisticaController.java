package com.edfinal.controller;

import com.edfinal.domain.EstadisticaMateria;
import com.edfinal.service.TareaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estadisticas")
public class EstadisticaController {

    private final TareaService tareaService;

    public EstadisticaController(TareaService tareaService) {

        this.tareaService = tareaService;

    }

    @GetMapping
    public List<EstadisticaMateria> consultar() {

        return tareaService.obtenerEstadisticasPorMateria();

    }

}
