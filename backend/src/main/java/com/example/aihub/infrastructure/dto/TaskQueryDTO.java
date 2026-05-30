package com.example.aihub.infrastructure.dto;

import lombok.Data;

@Data
public class TaskQueryDTO {
    private Long projectId;
    private String status;
}
