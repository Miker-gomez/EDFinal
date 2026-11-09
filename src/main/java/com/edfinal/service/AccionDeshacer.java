package com.edfinal.service;

import com.edfinal.domain.Tarea;
import java.util.Objects;

/**
 * Representa una acción reversible almacenada en la pila de deshacer (ArrayDeque).
 * Guarda la instantánea (snapshot) del estado de la tarea previo a la modificación
 * para permitir la restauración exacta tanto en memoria como en la base de datos.
 */
public record AccionDeshacer(
        TipoAccion tipo,
        Tarea tareaPrevia
) {
    public enum TipoAccion {
        COMPLETAR,
        MODIFICAR
    }

    public AccionDeshacer {
        Objects.requireNonNull(tipo, "El tipo de acción no puede ser nulo");
        Objects.requireNonNull(tareaPrevia, "La tarea previa no puede ser nula");
    }
}
