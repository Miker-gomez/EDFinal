package com.edfinal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Entrada de POST /api/profesores
public record ProfesorRequest(

        @NotBlank(message = "El nombre no puede estar vacío")
        @Size(max = 100, message = "El nombre admite máximo 100 caracteres")
        String nombre,

        @NotBlank(message = "El correo no puede estar vacío")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 120, message = "El correo admite máximo 120 caracteres")
        String correo

) { }
