package com.capco.brsp.synthesisengine.exception;

/**
 * Exception thrown when an LLM provider is not found or not supported.
 */
public class LLMProviderNotFoundException extends LLMServiceException {
    
    public LLMProviderNotFoundException(String provider) {
        super("Unsupported LLM provider: " + provider, provider);
    }
    
    public LLMProviderNotFoundException(String message, String provider) {
        super(message, provider);
    }
}