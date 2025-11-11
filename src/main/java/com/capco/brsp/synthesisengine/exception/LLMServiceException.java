package com.capco.brsp.synthesisengine.exception;

/**
 * Base exception class for LLM service related errors.
 */
public class LLMServiceException extends RuntimeException {
    private final String provider;
    private final String errorCode;
    
    public LLMServiceException(String message, String provider) {
        this(message, provider, null, null);
    }
    
    public LLMServiceException(String message, String provider, String errorCode) {
        this(message, provider, errorCode, null);
    }
    
    public LLMServiceException(String message, String provider, Throwable cause) {
        this(message, provider, null, cause);
    }
    
    public LLMServiceException(String message, String provider, String errorCode, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.errorCode = errorCode;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}