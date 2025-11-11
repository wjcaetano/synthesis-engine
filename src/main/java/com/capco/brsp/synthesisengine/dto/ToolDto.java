package com.capco.brsp.synthesisengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ToolDto {
    @JsonProperty("uuid")
    private UUID uuid = UUID.randomUUID();

    @JsonProperty("name")
    private String name;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;
}
