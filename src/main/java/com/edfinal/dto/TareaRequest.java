package com.edfinal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

// Entrada de POST /api/tareas y PUT /api/tareas/{id}
public record TareaRequest(

        @NotBlank(message = "El título no puede estar vacío")
        @Size(max = 150, message = "El título admite máximo 150 caracteres")
        String titulo,

        @NotNull(message = "La materia es obligatoria")
        Integer materiaId,

        @NotNull(message = "La prioridad es obligatoria")
        @Min(value = 1, message = "La prioridad debe estar entre 1 y 5")
        @Max(value = 5, message = "La prioridad debe estar entre 1 y 5")
        Integer prioridad,

        @NotNull(message = "La fecha límite es obligatoria")
        LocalDateTime fechaLimite

) { }
