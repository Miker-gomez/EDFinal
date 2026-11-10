package com.edfinal.domain;

// Fila de sp_estadisticas_entrega: conteo de tareas por materia y estado (F08).
public record EstadisticaMateria(
        int materiaId,
        String materia,
        long total,
        long pendientes,
        long completadas,
        long vencidas
) {
}