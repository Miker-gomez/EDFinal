package com.edfinal.domain;

import com.edfinal.structures.IIdentificable;

import java.time.LocalDateTime;

// Tarea académica. El orden de los componentes coincide con el JSON de salida de la API
// prioridad es el nivel de 1 a 5 (igual al id de tipos_prioridad)
public record Tarea(
        String id,
        String titulo,
        int materiaId,
        String materia,
        int prioridad,
        LocalDateTime fechaLimite,
        EstadoTarea estado
) implements Comparable<Tarea>, IIdentificable {

    // "Mayor" = más urgente: mayor prioridad y, en empate, fecha límite más cercana
    @Override
    public int compareTo(Tarea otra) {

        int resultado = 0;

        resultado = Integer.compare(this.prioridad, otra.prioridad);
        if ( resultado != 0 ){
            return resultado;
        }

        // La fecha más cercana es más urgente, por eso se compara al revés
        return otra.fechaLimite.compareTo(this.fechaLimite);

    }

    @Override
    public String getId() {
        return id;
    }

    public Tarea conEstado(EstadoTarea nuevoEstado) {
        return new Tarea(id, titulo, materiaId, materia, prioridad, fechaLimite, nuevoEstado);
    }

}
