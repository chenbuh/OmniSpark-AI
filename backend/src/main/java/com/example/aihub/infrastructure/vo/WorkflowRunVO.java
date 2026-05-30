package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowRunVO {
    private Long id;
    private Long workflowId;
    private Long projectId;
    private String status;
    private Integer currentStep;
    private String stepsResultJson;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
