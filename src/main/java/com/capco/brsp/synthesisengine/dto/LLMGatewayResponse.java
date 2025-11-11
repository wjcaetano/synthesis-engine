package com.capco.brsp.synthesisengine.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class LLMGatewayResponse {
    private String statusCode;
    private boolean error;
    private String errorMessage;
    private String thread;
    private boolean threadOpen;
    private String llmProvider;
    private String llmModel;
    private String llmModelName;
    private String llmVersion;
    private String llmResponse;
    private List<String> llmAnswers;
}
