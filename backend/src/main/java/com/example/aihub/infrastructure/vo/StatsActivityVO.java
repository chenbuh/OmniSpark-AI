package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StatsActivityVO {
    private String type;
    private String title;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
