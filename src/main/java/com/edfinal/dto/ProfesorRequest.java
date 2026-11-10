package com.edfinal.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// Entrada de POST /api/profesores
public record ProfesorRequest(

        @NotBlank(message = "El nombre no puede estar vacío")
        String nombre,

        @NotBlank(message = "El correo no puede estar vacío")
        @Email(message = "El correo no tiene un formato válido")
        String correo

) { }
