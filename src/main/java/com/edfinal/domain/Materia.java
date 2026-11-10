package com.edfinal.domain;

// profesor = nombre del docente, resuelto por JOIN para listar sin segunda consulta.
public record Materia(int id, String nombre, int profesorId, String profesor) {
}