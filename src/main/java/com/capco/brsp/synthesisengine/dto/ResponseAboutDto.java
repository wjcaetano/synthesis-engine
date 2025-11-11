package com.capco.brsp.synthesisengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
@Builder
public class ResponseAboutDto {
    private String appName;
    private String appCurrentVersion;
    private String apiLatestVersion;
    private Map<String, Object> otherAttributes;
}
