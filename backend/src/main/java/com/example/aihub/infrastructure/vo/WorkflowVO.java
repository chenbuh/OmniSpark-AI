package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowVO {
    private Long id;
    private Long projectId;
    private String name;
    private String description;
    private String stepsJson;
    private Integer status;
    private LocalDateTime createdAt;
}
