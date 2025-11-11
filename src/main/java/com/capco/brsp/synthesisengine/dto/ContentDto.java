package com.capco.brsp.synthesisengine.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentDto {
    @JsonProperty("error")
    private boolean error;

    @JsonProperty("errorMessage")
    private String errorMessage;

    @JsonProperty("flowKey")
    private String flowKey;

    @JsonProperty("path")
    private String path;

    @JsonProperty("historyIndex")
    private String historyIndex;

    @JsonProperty("content")
    private String content;
}
