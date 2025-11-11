package com.capco.brsp.synthesisengine.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for estimating token counts for different LLM models.
 * This provides more accurate token estimation than simple character counting.
 */
@Slf4j
public class TokenizerUtils {

    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[!\"#$%&'()*+,-./:;<=>?@\\[\\]^_`{|}~]");
    
    // Average tokens per word for different languages/models
    private static final Map<String, Double> TOKENS_PER_WORD = new HashMap<>();
    
    static {
        // Initialize with common models and their average tokens per word
        TOKENS_PER_WORD.put("gpt-3.5-turbo", 1.3);
        TOKENS_PER_WORD.put("gpt-4", 1.3);
        TOKENS_PER_WORD.put("claude-3", 1.4);
        TOKENS_PER_WORD.put("titan", 1.25);
        TOKENS_PER_WORD.put("gemini", 1.4);
        TOKENS_PER_WORD.put("default", 1.3); // Default fallback
    }
    
    /**
     * Estimates the number of tokens in the given text for the specified model.
     * Uses a more sophisticated approach than simple character counting.
     *
     * @param text The text to estimate tokens for
     * @param model The model name (e.g., "gpt-3.5-turbo", "claude-3")
     * @return Estimated token count
     */
    public static int estimateTokenCount(String text, String model) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Count words
        Matcher wordMatcher = WORD_PATTERN.matcher(text);
        int wordCount = 0;
        while (wordMatcher.find()) {
            wordCount++;
        }
        
        // Count whitespace
        Matcher whitespaceMatcher = WHITESPACE_PATTERN.matcher(text);
        int whitespaceCount = 0;
        while (whitespaceMatcher.find()) {
            whitespaceCount++;
        }
        
        // Count punctuation
        Matcher punctuationMatcher = PUNCTUATION_PATTERN.matcher(text);
        int punctuationCount = 0;
        while (punctuationMatcher.find()) {
            punctuationCount++;
        }
        
        // Get tokens per word for the model
        Double tokensPerWord = TOKENS_PER_WORD.getOrDefault(
                model != null ? model.toLowerCase() : "default", 
                TOKENS_PER_WORD.get("default"));
        
        // Calculate estimated tokens
        int estimatedTokens = (int) Math.ceil(wordCount * tokensPerWord) + whitespaceCount + punctuationCount;
        
        log.debug("Token estimation for model {}: {} words, {} whitespace, {} punctuation, {} estimated tokens",
                model, wordCount, whitespaceCount, punctuationCount, estimatedTokens);
        
        return estimatedTokens;
    }
    
    /**
     * Estimates the number of tokens in the given text using the default model.
     *
     * @param text The text to estimate tokens for
     * @return Estimated token count
     */
    public static int estimateTokenCount(String text) {
        return estimateTokenCount(text, "default");
    }
}