package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectShareVO {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long teamId;
    private String teamName;
    private String permission;
    private LocalDateTime createdAt;
}
