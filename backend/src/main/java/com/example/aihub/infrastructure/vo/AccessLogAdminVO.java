package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccessLogAdminVO {
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
    private IpGeoInfoVO ipGeo;
}
