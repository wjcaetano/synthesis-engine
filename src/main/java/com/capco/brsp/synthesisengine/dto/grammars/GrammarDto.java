package com.capco.brsp.synthesisengine.dto.grammars;


import com.capco.brsp.synthesisengine.utils.ConcurrentLinkedHashMap;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrammarDto {
    @JsonProperty("reference")
    private String reference;

    @JsonProperty("engine")
    private GrammarEngine engine;

    @JsonProperty("dependencies")
    private Map<String, GrammarScriptDto> dependencies = new ConcurrentLinkedHashMap<>();

    @JsonProperty("lexer")
    private GrammarScriptDto lexer;

    @JsonProperty("parser")
    private GrammarScriptDto parser;

    @JsonProperty("entryRule")
    private String entryRule;
}
