package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserIpProfileVO {
    private String ip;
    private IpGeoInfoVO ipGeo;
    private Long loginCount;
    private Long auditCount;
    private Long accessCount;
    private Long totalCount;
    private LocalDateTime lastSeenAt;
}
