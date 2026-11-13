package com.edfinal.exception;

// La materia todavía tiene tareas pendientes, vencidas o completadas
public class MateriaConTareasException extends RuntimeException {

    public MateriaConTareasException(String mensaje) {

        super(mensaje);

    }

}
