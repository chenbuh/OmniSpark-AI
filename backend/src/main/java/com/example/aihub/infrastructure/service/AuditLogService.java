package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.aihub.common.security.ClientIpResolver;
import com.example.aihub.common.util.PagingUtil;
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
    private final ClientIpResolver clientIpResolver;

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
        String ip = request != null ? clientIpResolver.resolve(request) : null;
        log(userId, username, action, null, null, detail, ip);
    }

    /** 分页查询审计日志,返回 PageResult。 */
    public com.example.aihub.common.result.PageResult<AuditLogVO> page(String action, Long userId, long page, long size) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(size, 20);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            wrapper.eq(AuditLog::getAction, action);
        }
        if (userId != null) {
            wrapper.eq(AuditLog::getUserId, userId);
        }
        wrapper.orderByDesc(AuditLog::getId);
        var p = auditLogMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(safePage, safePageSize), wrapper);
        List<AuditLogVO> records = p.getRecords().stream()
                .map(item -> VoMapper.copy(item, AuditLogVO.class))
                .toList();
        return new com.example.aihub.common.result.PageResult<>(p.getTotal(), p.getPages(), records);
    }

    public List<String> actions(Long userId) {
        QueryWrapper<AuditLog> wrapper = new QueryWrapper<AuditLog>()
                .select("DISTINCT action")
                .isNotNull("action");
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        return auditLogMapper.selectObjs(wrapper).stream()
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    public long countOlderThan(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        Long count = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
        return count == null ? 0L : count;
    }

    /** 删除 N 天前的审计日志,返回删除条数(由控制器做权限与下限校验)。 */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public long deleteOlderThan(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        return auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
    }
}
