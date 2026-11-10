package com.edfinal.exception;

// El profesor solicitado no existe
public class ProfesorNoEncontradoException extends RuntimeException {

    public ProfesorNoEncontradoException(String mensaje) {

        super(mensaje);

    }

}
