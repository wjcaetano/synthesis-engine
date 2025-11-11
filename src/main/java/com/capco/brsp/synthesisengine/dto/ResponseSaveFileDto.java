package com.capco.brsp.synthesisengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class ResponseSaveFileDto {
    private Boolean error;
    private String errorMessage;
    private String errorStack;
    private String message;
}
