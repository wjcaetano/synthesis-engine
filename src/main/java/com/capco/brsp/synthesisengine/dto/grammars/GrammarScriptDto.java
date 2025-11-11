package com.capco.brsp.synthesisengine.dto.grammars;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GrammarScriptDto {
    @JsonProperty("reference")
    private String reference;

    @JsonProperty("body")
    private String body;
}
