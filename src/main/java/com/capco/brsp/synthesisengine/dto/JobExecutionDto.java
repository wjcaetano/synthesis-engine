package com.capco.brsp.synthesisengine.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Data
public class JobExecutionDto {
    private String jobName;
    private String status;
    private int completedSteps;
    private int totalSteps;
    private int completionPercentage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long totalTime;
    private int numberOfAttempts;
    private String currentStepName;
    private String currentStepStatus;
    private List<StepInfoDto> steps;
}
