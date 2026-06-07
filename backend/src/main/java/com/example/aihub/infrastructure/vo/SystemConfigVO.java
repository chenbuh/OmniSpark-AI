package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SystemConfigVO {
    private Long id;
    private String configKey;
    private String configValue;
    private String configGroup;
    private String remark;
    private boolean sensitive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
