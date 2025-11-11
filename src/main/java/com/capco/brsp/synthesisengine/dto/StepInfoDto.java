package com.capco.brsp.synthesisengine.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StepInfoDto {
    private String name;
    private String status;
    private int numberOfAttempts;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long totalTime;
}
