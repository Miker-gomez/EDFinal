package com.edfinal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Entrada de POST /api/materias y PUT /api/materias/{id}
public record MateriaRequest(

        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres")
        String nombre,

        @NotNull(message = "El profesor es obligatorio")
        Integer profesorId

) { }
