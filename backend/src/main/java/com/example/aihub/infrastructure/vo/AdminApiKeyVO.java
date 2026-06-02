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
    private String scope;
    private LocalDateTime expiresAt;
    private Integer dailyQuota;
    private Integer dailyUsed;
    private String lastUsedIp;
    private String lastUserAgent;
    private String frozenReason;
    private Integer riskScore;
    private Integer status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
