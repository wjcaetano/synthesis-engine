package com.capco.brsp.synthesisengine.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonolithPromptDto {
    private MonolithInputsDto inputs;
    private String prompt;
}
