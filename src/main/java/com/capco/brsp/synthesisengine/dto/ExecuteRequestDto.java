package com.capco.brsp.synthesisengine.dto;

import com.capco.brsp.synthesisengine.enums.EnumEvaluateTypes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ExecuteRequestDto {
    @JsonProperty("path")
    private String path;
    @JsonProperty("type")
    private EnumEvaluateTypes type;
    @JsonProperty("expression")
    private String expression;
}
