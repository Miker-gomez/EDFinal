package com.edfinal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Entrada de POST /api/materias y PUT /api/materias/{id}
public record MateriaRequest(

        @NotBlank(message = "El nombre no puede estar vacío")
        String nombre,

        @NotNull(message = "El profesor es obligatorio")
        Integer profesorId

) { }
