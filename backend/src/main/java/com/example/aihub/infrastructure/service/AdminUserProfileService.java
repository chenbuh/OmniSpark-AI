package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.entity.AccessLog;
import com.example.aihub.infrastructure.entity.AuditLog;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.AccessLogMapper;
import com.example.aihub.infrastructure.mapper.AuditLogMapper;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.AccessLogAdminVO;
import com.example.aihub.infrastructure.vo.AdminUserProfileVO;
import com.example.aihub.infrastructure.vo.AuditLogVO;
import com.example.aihub.infrastructure.vo.IpGeoInfoVO;
import com.example.aihub.infrastructure.vo.LoginLogAdminVO;
import com.example.aihub.infrastructure.vo.UserIpProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminUserProfileService {
    private static final int RECENT_LIMIT = 10;
    private static final int COMMON_IP_LIMIT = 10;
    private static final int COMMON_IP_QUERY_LIMIT = 20;

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final AuditLogMapper auditLogMapper;
    private final AccessLogMapper accessLogMapper;
    private final IpGeoLookupService ipGeoLookupService;

    public AdminUserProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }

        List<LoginLog> loginLogs = loginLogMapper.selectList(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .orderByDesc(LoginLog::getId)
                .last("LIMIT " + RECENT_LIMIT));
        List<AuditLog> auditLogs = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getUserId, userId)
                .orderByDesc(AuditLog::getId)
                .last("LIMIT " + RECENT_LIMIT));
        List<AccessLog> accessLogs = accessLogMapper.selectList(new LambdaQueryWrapper<AccessLog>()
                .eq(AccessLog::getUserId, userId)
                .orderByDesc(AccessLog::getId)
                .last("LIMIT " + RECENT_LIMIT));

        Map<String, IpGeoInfoVO> ipGeoMap = ipGeoLookupService.resolveBatch(collectIps(loginLogs, auditLogs, accessLogs));

        AdminUserProfileVO profile = new AdminUserProfileVO();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setNickname(user.getNickname());
        profile.setAvatar(user.getAvatar());
        profile.setRole(user.getRole());
        profile.setStatus(user.getStatus());
        profile.setTotpEnabled(user.getTotpEnabled());
        profile.setCreatedAt(user.getCreatedAt());
        profile.setTotalLoginCount(resolveCount(loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLog>().eq(LoginLog::getUserId, userId))));
        profile.setTotalAuditCount(resolveCount(auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>().eq(AuditLog::getUserId, userId))));
        profile.setTotalAccessCount(resolveCount(accessLogMapper.selectCount(new LambdaQueryWrapper<AccessLog>().eq(AccessLog::getUserId, userId))));
        profile.setRecentLoginLogs(toLoginLogRecords(loginLogs, ipGeoMap));
        profile.setRecentAuditLogs(toAuditLogRecords(auditLogs, ipGeoMap));
        profile.setRecentAccessLogs(toAccessLogRecords(accessLogs, ipGeoMap));
        profile.setLatestLogin(profile.getRecentLoginLogs().isEmpty() ? null : profile.getRecentLoginLogs().get(0));
        profile.setLatestAudit(profile.getRecentAuditLogs().isEmpty() ? null : profile.getRecentAuditLogs().get(0));
        profile.setLatestAccess(profile.getRecentAccessLogs().isEmpty() ? null : profile.getRecentAccessLogs().get(0));
        profile.setCommonIps(buildCommonIps(userId));
        return profile;
    }

    private List<String> collectIps(List<LoginLog> loginLogs, List<AuditLog> auditLogs, List<AccessLog> accessLogs) {
        List<String> ips = new ArrayList<>();
        loginLogs.stream().map(LoginLog::getIp).filter(Objects::nonNull).forEach(ips::add);
        auditLogs.stream().map(AuditLog::getIp).filter(Objects::nonNull).forEach(ips::add);
        accessLogs.stream().map(AccessLog::getClientIp).filter(Objects::nonNull).forEach(ips::add);
        return ips;
    }

    private List<LoginLogAdminVO> toLoginLogRecords(List<LoginLog> logs, Map<String, IpGeoInfoVO> ipGeoMap) {
        return logs.stream().map(item -> {
            LoginLogAdminVO vo = VoMapper.copy(item, LoginLogAdminVO.class);
            vo.setIpGeo(ipGeoMap.get(item.getIp()));
            return vo;
        }).toList();
    }

    private List<AuditLogVO> toAuditLogRecords(List<AuditLog> logs, Map<String, IpGeoInfoVO> ipGeoMap) {
        return logs.stream().map(item -> {
            AuditLogVO vo = VoMapper.copy(item, AuditLogVO.class);
            vo.setIpGeo(ipGeoMap.get(item.getIp()));
            return vo;
        }).toList();
    }

    private List<AccessLogAdminVO> toAccessLogRecords(List<AccessLog> logs, Map<String, IpGeoInfoVO> ipGeoMap) {
        return logs.stream().map(item -> {
            AccessLogAdminVO vo = VoMapper.copy(item, AccessLogAdminVO.class);
            vo.setIpGeo(ipGeoMap.get(item.getClientIp()));
            return vo;
        }).toList();
    }

    private List<UserIpProfileVO> buildCommonIps(Long userId) {
        Map<String, UserIpProfileVO> merged = new LinkedHashMap<>();
        mergeIpStats(merged, topLoginIpStats(userId), "login");
        mergeIpStats(merged, topAuditIpStats(userId), "audit");
        mergeIpStats(merged, topAccessIpStats(userId), "access");

        Map<String, IpGeoInfoVO> ipGeoMap = ipGeoLookupService.resolveBatch(new ArrayList<>(merged.keySet()));
        return merged.values().stream()
                .peek(item -> item.setIpGeo(ipGeoMap.get(item.getIp())))
                .sorted(Comparator
                        .comparing(UserIpProfileVO::getTotalCount, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(UserIpProfileVO::getLastSeenAt, Comparator.nullsLast(LocalDateTime::compareTo))
                        .reversed())
                .limit(COMMON_IP_LIMIT)
                .toList();
    }

    private List<Map<String, Object>> topLoginIpStats(Long userId) {
        return loginLogMapper.selectMaps(new QueryWrapper<LoginLog>()
                .select("ip AS ip", "COUNT(*) AS hitCount", "MAX(created_at) AS lastSeenAt")
                .eq("user_id", userId)
                .isNotNull("ip")
                .ne("ip", "")
                .groupBy("ip")
                .orderByDesc("hitCount")
                .orderByDesc("lastSeenAt")
                .last("LIMIT " + COMMON_IP_QUERY_LIMIT));
    }

    private List<Map<String, Object>> topAuditIpStats(Long userId) {
        return auditLogMapper.selectMaps(new QueryWrapper<AuditLog>()
                .select("ip AS ip", "COUNT(*) AS hitCount", "MAX(created_at) AS lastSeenAt")
                .eq("user_id", userId)
                .isNotNull("ip")
                .ne("ip", "")
                .groupBy("ip")
                .orderByDesc("hitCount")
                .orderByDesc("lastSeenAt")
                .last("LIMIT " + COMMON_IP_QUERY_LIMIT));
    }

    private List<Map<String, Object>> topAccessIpStats(Long userId) {
        return accessLogMapper.selectMaps(new QueryWrapper<AccessLog>()
                .select("client_ip AS ip", "COUNT(*) AS hitCount", "MAX(created_at) AS lastSeenAt")
                .eq("user_id", userId)
                .isNotNull("client_ip")
                .ne("client_ip", "")
                .groupBy("client_ip")
                .orderByDesc("hitCount")
                .orderByDesc("lastSeenAt")
                .last("LIMIT " + COMMON_IP_QUERY_LIMIT));
    }

    private void mergeIpStats(Map<String, UserIpProfileVO> merged, List<Map<String, Object>> rows, String source) {
        for (Map<String, Object> row : rows) {
            String ip = normalizeText(row.get("ip"));
            if (ip.isBlank()) {
                continue;
            }
            long count = toLong(row.get("hitCount"));
            LocalDateTime lastSeenAt = toLocalDateTime(row.get("lastSeenAt"));
            UserIpProfileVO item = merged.computeIfAbsent(ip, key -> {
                UserIpProfileVO profile = new UserIpProfileVO();
                profile.setIp(key);
                profile.setLoginCount(0L);
                profile.setAuditCount(0L);
                profile.setAccessCount(0L);
                profile.setTotalCount(0L);
                return profile;
            });
            switch (source) {
                case "login" -> item.setLoginCount(count);
                case "audit" -> item.setAuditCount(count);
                case "access" -> item.setAccessCount(count);
                default -> {
                }
            }
            item.setTotalCount(resolveCount(item.getLoginCount()) + resolveCount(item.getAuditCount()) + resolveCount(item.getAccessCount()));
            if (item.getLastSeenAt() == null || (lastSeenAt != null && lastSeenAt.isAfter(item.getLastSeenAt()))) {
                item.setLastSeenAt(lastSeenAt);
            }
        }
    }

    private long resolveCount(Long value) {
        return value == null ? 0L : value;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private String normalizeText(Object value) {
        return value instanceof String text ? text.trim() : "";
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return LocalDateTime.parse(text.trim().replace(" ", "T"));
            } catch (DateTimeParseException ignored) {
                try {
                    return OffsetDateTime.parse(text.trim()).toLocalDateTime();
                } catch (DateTimeParseException ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }
}
