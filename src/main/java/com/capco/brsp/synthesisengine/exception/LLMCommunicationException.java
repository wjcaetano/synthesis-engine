package com.capco.brsp.synthesisengine.exception;

/**
 * Exception thrown when there's an error in communication with the LLM provider.
 * This includes network errors, timeouts, rate limits, etc.
 */
public class LLMCommunicationException extends LLMServiceException {
    
    public LLMCommunicationException(String message, String provider) {
        super(message, provider);
    }
    
    public LLMCommunicationException(String message, String provider, Throwable cause) {
        super(message, provider, cause);
    }
    
    public LLMCommunicationException(String message, String provider, String errorCode) {
        super(message, provider, errorCode);
    }
    
    public LLMCommunicationException(String message, String provider, String errorCode, Throwable cause) {
        super(message, provider, errorCode, cause);
    }
}