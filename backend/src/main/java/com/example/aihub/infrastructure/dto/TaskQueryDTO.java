package com.example.aihub.infrastructure.dto;

import lombok.Data;

@Data
public class TaskQueryDTO {
    private Long projectId;
    private String status;
    private Long page;
    private Long pageSize;
    private Integer limit;
}
