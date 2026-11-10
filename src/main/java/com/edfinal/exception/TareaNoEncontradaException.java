package com.edfinal.exception;

// La tarea solicitada no existe entre las activas
public class TareaNoEncontradaException extends RuntimeException {

    public TareaNoEncontradaException(String mensaje) {

        super(mensaje);

    }

}
