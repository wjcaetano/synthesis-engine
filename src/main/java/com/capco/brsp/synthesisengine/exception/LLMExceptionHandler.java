package com.capco.brsp.synthesisengine.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for LLM service related exceptions.
 */
@Slf4j
@ControllerAdvice
public class LLMExceptionHandler {

    @ExceptionHandler(LLMProviderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleProviderNotFoundException(LLMProviderNotFoundException ex) {
        log.error("LLM provider not found: {}", ex.getMessage(), ex);
        return createErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(LLMConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handleConfigurationException(LLMConfigurationException ex) {
        log.error("LLM configuration error: {}", ex.getMessage(), ex);
        return createErrorResponse(ex, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(LLMCommunicationException.class)
    public ResponseEntity<Map<String, Object>> handleCommunicationException(LLMCommunicationException ex) {
        log.error("LLM communication error: {}", ex.getMessage(), ex);
        return createErrorResponse(ex, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(LLMServiceException.class)
    public ResponseEntity<Map<String, Object>> handleServiceException(LLMServiceException ex) {
        log.error("LLM service error: {}", ex.getMessage(), ex);
        return createErrorResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Map<String, Object>> createErrorResponse(LLMServiceException ex, HttpStatus status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("provider", ex.getProvider());
        
        if (ex.getErrorCode() != null) {
            errorResponse.put("errorCode", ex.getErrorCode());
        }
        
        return new ResponseEntity<>(errorResponse, status);
    }
}