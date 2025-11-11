package com.capco.brsp.synthesisengine.dto;

import com.capco.brsp.synthesisengine.enums.EnumLLMGatewayRoles;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class LLMGatewayMessageDto {
    private EnumLLMGatewayRoles role;
    private String message;
}
