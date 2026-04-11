package com.atex.onecms.app.dam.ws;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized error handling for ContentApiException.
 * Applies globally so that plugin controllers (com.atex.customer.*) and
 * built-in controllers (com.atex.desk.api.*, com.atex.onecms.*) all get
 * consistent error responses matching the reference OneCMS format.
 */
@RestControllerAdvice
public class ContentApiExceptionHandler {

    private static final Logger LOG = Logger.getLogger(ContentApiExceptionHandler.class.getName());

    @ExceptionHandler(ContentApiException.class)
    public ResponseEntity<Map<String, Object>> handleContentApiException(ContentApiException ex) {
        if (ex.getHttpStatus().is5xxServerError()) {
            LOG.log(Level.SEVERE, ex.getMessage(), ex);
        } else {
            LOG.log(Level.FINE, ex.getMessage());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("extraInfo", Map.of());
        body.put("statusCode", ex.getDetailCode());
        body.put("message", ex.getMessage());

        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }
}
