package com.capco.brsp.synthesisengine.exception;

/**
 * Exception thrown when there's an issue with LLM configuration.
 */
public class LLMConfigurationException extends LLMServiceException {
    
    public LLMConfigurationException(String message, String provider) {
        super(message, provider);
    }
    
    public LLMConfigurationException(String message, String provider, Throwable cause) {
        super(message, provider, cause);
    }
    
    public LLMConfigurationException(String message, String provider, String errorCode) {
        super(message, provider, errorCode);
    }
    
    public LLMConfigurationException(String message, String provider, String errorCode, Throwable cause) {
        super(message, provider, errorCode, cause);
    }
}