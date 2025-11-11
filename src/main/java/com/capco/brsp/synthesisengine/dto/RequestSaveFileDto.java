package com.capco.brsp.synthesisengine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RequestSaveFileDto {
    @JsonProperty("path")
    private String path;
    @JsonProperty("content")
    private String content;
}
