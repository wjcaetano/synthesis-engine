package com.capco.brsp.synthesisengine.dto.grammars;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GrammarDto {
    @JsonProperty("reference")
    private String reference;

    @JsonProperty("engine")
    private GrammarEngine engine;

    @JsonProperty("lexer")
    private GrammarScriptDto lexer;

    @JsonProperty("parser")
    private GrammarScriptDto parser;

    @JsonProperty("entryRule")
    private String entryRule;
}
