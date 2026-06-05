package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PasswordChangeResultVO {
    private Long userId;
    private boolean changed;
    private LocalDateTime updatedAt;
}
