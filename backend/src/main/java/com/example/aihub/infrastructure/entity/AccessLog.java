package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("access_log")
public class AccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long apiKeyId;
    private String clientIp;
    private String userAgent;
    private String method;
    private String path;
    private String queryString;
    private Integer statusCode;
    private Long durationMs;
    private Integer rateLimited;
    private String riskReason;
    private LocalDateTime createdAt;
}
