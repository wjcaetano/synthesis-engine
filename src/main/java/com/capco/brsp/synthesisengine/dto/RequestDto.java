package com.capco.brsp.synthesisengine.dto;

import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Builder
@AllArgsConstructor
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequestDto {
    @JsonProperty("recipeContent") // FrontEnd: recipe.yaml
    private String recipeContent;
    @JsonProperty("obfuscationMap") // FrontEnd: map.json
    private Map<String, String> obfuscationMap = new ConcurrentLinkedHashMap<>();
    @JsonProperty("configs")
    private Map<String, Object> configs = new ConcurrentLinkedHashMap<>();
    @JsonProperty("files")
    private Map<String, String> files = new ConcurrentLinkedHashMap<>();
}
