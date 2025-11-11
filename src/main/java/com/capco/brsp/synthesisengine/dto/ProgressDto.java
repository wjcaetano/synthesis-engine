package com.capco.brsp.synthesisengine.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ProgressDto {
    private UUID projectUUID;
    private Long version;
    private Object customData;
    private List<FileDto> files;
    private String projectContext;
    private JobExecutionDto job;
}
