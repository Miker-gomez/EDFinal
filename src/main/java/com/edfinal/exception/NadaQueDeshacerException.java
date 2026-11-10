package com.edfinal.exception;

// No hay acciones para deshacer
public class NadaQueDeshacerException extends RuntimeException {

    public NadaQueDeshacerException(String mensaje) {

        super(mensaje);

    }

}
