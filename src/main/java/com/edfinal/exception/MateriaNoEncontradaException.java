package com.edfinal.exception;

// La materia solicitada no existe
public class MateriaNoEncontradaException extends RuntimeException {

    public MateriaNoEncontradaException(String mensaje) {

        super(mensaje);

    }

}
