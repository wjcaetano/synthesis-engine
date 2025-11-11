package com.capco.brsp.synthesisengine.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class FileDto {
    private String path;
    private String fullPath;
    private String mimeType;
    @JsonIgnore
    private LocalDateTime createdAt;
    @JsonIgnore
    private LocalDateTime updatedAt;
    private String content;
    private List<TransformDto> history;
}
