package com.edfinal.domain;

import java.time.LocalDateTime;

/**
 * Tarea académica (modelo de dominio, inmutable).
 * El orden de los componentes coincide con el JSON de salida de la API.
 * materiaId y materia (nombre) los resuelve el repositorio con un JOIN;
 * prioridad es el nivel 1..5 (igual al id de tipos_prioridad).
 */
public record Tarea(
        String id,
        String titulo,
        int materiaId,
        String materia,
        int prioridad,
        LocalDateTime fechaLimite,
        EstadoTarea estado
) implements Comparable<Tarea> {

    /** "Mayor" = más urgente: mayor prioridad; en empate, fecha límite más cercana. */
    @Override
    public int compareTo(Tarea otra) {
        int porPrioridad = Integer.compare(this.prioridad, otra.prioridad);
        if (porPrioridad != 0) {
            return porPrioridad;
        }
        // fecha más cercana => más urgente => debe resultar "mayor"
        return otra.fechaLimite.compareTo(this.fechaLimite);
    }

    public Tarea conEstado(EstadoTarea nuevoEstado) {
        return new Tarea(id, titulo, materiaId, materia, prioridad, fechaLimite, nuevoEstado);
    }
}