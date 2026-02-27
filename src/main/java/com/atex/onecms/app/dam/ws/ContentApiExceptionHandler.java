package com.atex.onecms.app.dam.ws;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralized error handling for DAM endpoints.
 * Converts ContentApiException to JSON error responses.
 */
@RestControllerAdvice(basePackages = "com.atex.onecms.app.dam.ws")
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
        body.put("error", ex.getMessage());
        body.put("status", ex.getHttpStatus().value());
        body.put("detailCode", ex.getDetailCode());

        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }
}
