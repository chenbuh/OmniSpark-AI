package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminApiKeyVO {
    private Long id;
    private Long userId;
    private String username;
    private String name;
    private String keyPrefix;
    private Integer status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
