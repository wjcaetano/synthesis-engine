package com.capco.brsp.synthesisengine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryMessageDto {
    private UUID id = UUID.randomUUID();
    private String role;
    private String content;
    private LocalDateTime timeStamp = LocalDateTime.now();
    private int tokenCount;
    private boolean isPermanent;
    private double relevanceScore;
}
