package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@TableName("api_key")
public class ApiKey {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String keyPrefix;
    private String keyHash;
    private String permissions;
    private String scope;
    private LocalDateTime expiresAt;
    private Integer dailyQuota;
    private Integer dailyUsed;
    private LocalDate quotaResetDate;
    private String lastUsedIp;
    private String lastUserAgent;
    private String frozenReason;
    private Integer riskScore;
    private Integer status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
