package com.capco.brsp.synthesisengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Script {
    @JsonProperty("reference")
    private String reference;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private ScriptType type;

    @JsonProperty("inject")
    private Map<String, Object> inject;

    @JsonProperty("extract")
    private Map<String, Object> extract;

    @JsonProperty("body")
    private String body;

    public enum ScriptType {
        PYTHON,
        GROOVY
    }
}
