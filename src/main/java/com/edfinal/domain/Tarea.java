package com.edfinal.domain;

import java.time.LocalDate;

public record Tarea(
        String id,
        String materia,
        String titulo,
        int prioridad,
        LocalDate fechaLimite,
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
        return new Tarea(id, materia, titulo, prioridad, fechaLimite, nuevoEstado);
    }
}