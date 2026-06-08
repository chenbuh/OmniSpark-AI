package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AdminUserProfileVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer status;
    private Integer totpEnabled;
    private LocalDateTime createdAt;

    private Long totalLoginCount;
    private Long totalAuditCount;
    private Long totalAccessCount;

    private LoginLogAdminVO latestLogin;
    private AuditLogVO latestAudit;
    private AccessLogAdminVO latestAccess;

    private List<UserIpProfileVO> commonIps;
    private List<LoginLogAdminVO> recentLoginLogs;
    private List<AuditLogVO> recentAuditLogs;
    private List<AccessLogAdminVO> recentAccessLogs;
}
