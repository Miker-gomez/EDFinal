package com.edfinal.controller;

import com.edfinal.dto.ErrorResponse;
import com.edfinal.exception.MateriaConTareasException;
import com.edfinal.exception.MateriaNoEncontradaException;
import com.edfinal.exception.NadaQueDeshacerException;
import com.edfinal.exception.ProfesorNoEncontradoException;
import com.edfinal.exception.TareaNoEncontradaException;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;

// Manejo centralizado de errores: toda respuesta de error sale como { status, mensaje }
@Log4j2
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> alValidar(MethodArgumentNotValidException ex) {

        String mensaje = "Datos inválidos";
        if (ex.getBindingResult().getFieldError() != null) {
            mensaje = ex.getBindingResult().getFieldError().getDefaultMessage();
        }
        return responder(HttpStatus.BAD_REQUEST, mensaje);

    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> alLeerElCuerpo(HttpMessageNotReadableException ex) {

        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido");

    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> alConvertirParametro(MethodArgumentTypeMismatchException ex) {

        return responder(HttpStatus.BAD_REQUEST, "El parámetro " + ex.getName() + " no es válido");

    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> alNoExistirLaRuta(NoResourceFoundException ex) {

        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado");

    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> alUsarMetodoNoPermitido(HttpRequestMethodNotSupportedException ex) {

        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido");

    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> alUsarTipoDeContenidoNoSoportado(HttpMediaTypeNotSupportedException ex) {

        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido debe ser application/json");

    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> alRecibirDatosInvalidos(IllegalArgumentException ex) {

        return responder(HttpStatus.BAD_REQUEST, ex.getMessage());

    }

    @ExceptionHandler({TareaNoEncontradaException.class, MateriaNoEncontradaException.class, ProfesorNoEncontradoException.class})
    public ResponseEntity<ErrorResponse> alNoEncontrar(RuntimeException ex) {

        return responder(HttpStatus.NOT_FOUND, ex.getMessage());

    }

    @ExceptionHandler({NadaQueDeshacerException.class, MateriaConTareasException.class})
    public ResponseEntity<ErrorResponse> alHaberConflicto(RuntimeException ex) {

        return responder(HttpStatus.CONFLICT, ex.getMessage());

    }

    // 23505 y 23503 -> 409; 23514, 23502 y la clase 22 (valor o tamaño no válido) -> 400; cualquier otro -> 500 genérico
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> alAccederADatos(DataAccessException ex) {

        String sqlState = buscarSqlState(ex);
        if ("23505".equals(sqlState)) {
            return responder(HttpStatus.CONFLICT, "El registro ya existe");
        }
        if ("23503".equals(sqlState)) {
            return responder(HttpStatus.CONFLICT, "El registro está relacionado con otros datos");
        }
        if ("23514".equals(sqlState) || "23502".equals(sqlState)) {
            return responder(HttpStatus.BAD_REQUEST, "Los datos no cumplen las restricciones");
        }
        if (sqlState != null && sqlState.startsWith("22")) {
            return responder(HttpStatus.BAD_REQUEST, "Los datos tienen un valor o un tamaño no válido");
        }
        log.error("Error de acceso a datos (SQLState {})", sqlState, ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");

    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> alFallarAlgoInesperado(Exception ex) {

        log.error("Error inesperado: {}", ex.getMessage(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");

    }

    // SQLState de la primera SQLException en la cadena de causas (null si no hay)
    private String buscarSqlState(Throwable causa) {

        while (causa != null) {

            if (causa instanceof SQLException sqlException) {
                return sqlException.getSQLState();
            }
            causa = causa.getCause();

        }
        return null;

    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String mensaje) {

        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), mensaje));

    }

}
