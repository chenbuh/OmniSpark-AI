package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.entity.AuditLog;
import com.example.aihub.infrastructure.mapper.AuditLogMapper;
import com.example.aihub.infrastructure.vo.AuditLogVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    public void log(Long userId, String username, String action,
                    String resourceType, Long resourceId, String detail, String ip) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        log.setIp(ip);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    public void log(Long userId, String username, String action, String detail, HttpServletRequest request) {
        String ip = request != null ? request.getRemoteAddr() : null;
        log(userId, username, action, null, null, detail, ip);
    }

    /** 分页查询审计日志,返回 PageResult。 */
    public com.example.aihub.common.result.PageResult<AuditLogVO> page(String action, Long userId, long page, long size) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuditLog::getAction, action);
        }
        if (userId != null) {
            wrapper.eq(AuditLog::getUserId, userId);
        }
        wrapper.orderByDesc(AuditLog::getId);
        var p = auditLogMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
        List<AuditLogVO> records = p.getRecords().stream()
                .map(item -> VoMapper.copy(item, AuditLogVO.class))
                .toList();
        return new com.example.aihub.common.result.PageResult<>(p.getTotal(), p.getPages(), records);
    }
}
