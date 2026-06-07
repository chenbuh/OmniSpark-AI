package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WebhookVO {
    private Long id;
    private String name;
    private String url;
    private String events;
    private Integer status;
    private boolean secretConfigured;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
