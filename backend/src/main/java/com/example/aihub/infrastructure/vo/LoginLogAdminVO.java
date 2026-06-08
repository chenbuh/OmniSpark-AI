package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogAdminVO {
    private Long id;
    private Long userId;
    private String username;
    private String ip;
    private String userAgent;
    private LocalDateTime createdAt;
    private IpGeoInfoVO ipGeo;
}
