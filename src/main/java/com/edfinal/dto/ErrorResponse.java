package com.edfinal.dto;

// Formato de error de toda la API: { "status": 404, "mensaje": "Tarea no encontrada" }
public record ErrorResponse(

        int status,
        String mensaje

) { }
