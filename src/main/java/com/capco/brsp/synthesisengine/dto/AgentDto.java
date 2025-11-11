package com.capco.brsp.synthesisengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AgentDto {
    @JsonProperty("uuid")
    private UUID uuid = UUID.randomUUID();

    @JsonProperty("name")
    private String name;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("model")
    private String model;

    @JsonProperty("deploymentName")
    private String deploymentName;

    @JsonProperty("embeddingModel")
    private String embeddingModel;

    @JsonProperty("isEmbedding")
    private Boolean isEmbedding;

    @JsonProperty("temperature")
    private Double temperature;

    @JsonProperty("responseFormat")
    private Object responseFormat;

    @JsonProperty("maxTokens")
    private Integer maxTokens;

    @JsonProperty("topP")
    @Builder.Default
    private Double topP = 1.0;

    @JsonProperty("frequencyPenalty")
    private Double frequencyPenalty;

    @JsonProperty("presencePenalty")
    private Double presencePenalty;

    @JsonProperty("topK")
    @Builder.Default
    private Integer topK = 1;

    @JsonProperty("maxTurns")
    private Integer maxTurns;

    @JsonProperty("retry")
    private Integer retry;

    @JsonProperty("systemInstructions")
    private String systemInstructions;

    @JsonProperty("stopSequences")
    private String stopSequences;

    @JsonProperty("before")
    private String before;

    @JsonProperty("during")
    private String during;

    @JsonProperty("after")
    private String after;

    @JsonProperty("tools")
    private List<String> tools;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
