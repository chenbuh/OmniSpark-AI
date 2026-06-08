package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.entity.AccessLog;
import com.example.aihub.infrastructure.mapper.AccessLogMapper;
import com.example.aihub.infrastructure.vo.AccessLogAdminVO;
import com.example.aihub.infrastructure.vo.IpGeoInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AccessLogService {
    private final AccessLogMapper accessLogMapper;
    private final IpGeoLookupService ipGeoLookupService;

    public void record(AccessLog log) {
        if (log == null) {
            return;
        }
        log.setCreatedAt(LocalDateTime.now());
        accessLogMapper.insert(log);
    }

    public PageResult<AccessLogAdminVO> page(String clientIp, Long userId, Long apiKeyId, String path,
                                             Integer statusCode, long page, long size) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(size, 20);
        LambdaQueryWrapper<AccessLog> wrapper = new LambdaQueryWrapper<>();
        if (clientIp != null && !clientIp.isBlank()) {
            wrapper.eq(AccessLog::getClientIp, clientIp);
        }
        if (userId != null) {
            wrapper.eq(AccessLog::getUserId, userId);
        }
        if (apiKeyId != null) {
            wrapper.eq(AccessLog::getApiKeyId, apiKeyId);
        }
        if (path != null && !path.isBlank()) {
            wrapper.like(AccessLog::getPath, path);
        }
        if (statusCode != null) {
            wrapper.eq(AccessLog::getStatusCode, statusCode);
        }
        wrapper.orderByDesc(AccessLog::getId);
        Page<AccessLog> result = accessLogMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        Map<String, IpGeoInfoVO> ipGeoMap = ipGeoLookupService.resolveBatch(result.getRecords().stream()
                .map(AccessLog::getClientIp)
                .filter(Objects::nonNull)
                .toList());
        List<AccessLogAdminVO> records = result.getRecords().stream().map(item -> {
            AccessLogAdminVO vo = VoMapper.copy(item, AccessLogAdminVO.class);
            vo.setIpGeo(ipGeoMap.get(item.getClientIp()));
            return vo;
        }).toList();
        return new PageResult<>(result.getTotal(), result.getPages(), records);
    }

    public Map<String, Object> summary(int minutes) {
        int safeMinutes = minutes <= 0 ? 60 : Math.min(minutes, 24 * 60);
        LocalDateTime since = LocalDateTime.now().minusMinutes(safeMinutes);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowMinutes", safeMinutes);
        result.put("total", accessLogMapper.selectCount(new LambdaQueryWrapper<AccessLog>()
                .ge(AccessLog::getCreatedAt, since)));
        result.put("rateLimited", accessLogMapper.selectCount(new LambdaQueryWrapper<AccessLog>()
                .ge(AccessLog::getCreatedAt, since)
                .eq(AccessLog::getRateLimited, 1)));
        result.put("riskHits", accessLogMapper.selectCount(new LambdaQueryWrapper<AccessLog>()
                .ge(AccessLog::getCreatedAt, since)
                .isNotNull(AccessLog::getRiskReason)));
        result.put("topIps", top("client_ip", since, 10));
        result.put("topPaths", top("path", since, 10));
        result.put("statusCodes", top("status_code", since, 10));
        return result;
    }

    private List<Map<String, Object>> top(String column, LocalDateTime since, int limit) {
        return accessLogMapper.selectMaps(new QueryWrapper<AccessLog>()
                .select(column + " AS name", "COUNT(*) AS count")
                .ge("created_at", since)
                .isNotNull(column)
                .groupBy(column)
                .orderByDesc("count")
                .last("LIMIT " + limit));
    }
}
