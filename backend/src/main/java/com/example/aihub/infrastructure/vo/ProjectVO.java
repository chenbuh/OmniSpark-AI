package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class ProjectVO {
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private Integer status;
    private java.time.LocalDateTime createdAt;
}
