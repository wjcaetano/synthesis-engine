package com.capco.brsp.synthesisengine.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CheckpointDto {
    private String name;
    private int transformIndex;
    private String content;
}
