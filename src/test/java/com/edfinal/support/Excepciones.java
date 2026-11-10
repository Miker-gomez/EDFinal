package com.edfinal.support;

// Apoyo de las pruebas: ejecuta un bloque y devuelve la excepción que lanzó
public class Excepciones {

    // Devuelve null si el bloque no lanzó nada
    public static Throwable capturar(Runnable bloque) {

        try {

            bloque.run();

        } catch (Throwable e) {
            return e;
        }
        return null;

    }

}
