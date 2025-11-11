package com.capco.brsp.synthesisengine.dto;

import com.capco.brsp.synthesisengine.enums.EnumEvaluateTypes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
public class EvaluateRequestDto {
    @JsonProperty("type")
    private EnumEvaluateTypes type;
    @JsonProperty("expression")
    private String expression;
    @JsonProperty("context")
    private Map<String, Object> context;
    @JsonProperty("llmProjectUUID")
    private UUID llmProjectUUID;
}
