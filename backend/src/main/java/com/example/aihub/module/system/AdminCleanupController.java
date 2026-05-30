package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.AuditLog;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/cleanup")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminCleanupController {
    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final AuditLogMapper auditLogMapper;
    private final LoginLogMapper loginLogMapper;

    @GetMapping("/preview")
    public ApiResult<Map<String, Object>> preview(@RequestParam(defaultValue = "30") int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("daysOld", daysOld);
        result.put("cutoffDate", cutoff.toString());

        long oldTasks = taskMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationTask>()
                .lt(GenerationTask::getCreatedAt, cutoff));
        result.put("oldTasks", oldTasks);

        long oldAssets = assetMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Asset>()
                .lt(Asset::getCreatedAt, cutoff));
        result.put("oldAssets", oldAssets);

        long oldAuditLogs = auditLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
        result.put("oldAuditLogs", oldAuditLogs);

        long oldLoginLogs = loginLogMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreatedAt, cutoff));
        result.put("oldLoginLogs", oldLoginLogs);

        return ApiResult.ok(result);
    }

    @DeleteMapping("/execute")
    public ApiResult<Map<String, Object>> execute(@RequestParam(defaultValue = "30") int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cutoffDate", cutoff.toString());

        long deletedTasks = taskMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationTask>()
                .lt(GenerationTask::getCreatedAt, cutoff));
        result.put("deletedTasks", deletedTasks);

        long deletedAssets = assetMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Asset>()
                .lt(Asset::getCreatedAt, cutoff));
        result.put("deletedAssets", deletedAssets);

        long deletedAuditLogs = auditLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
        result.put("deletedAuditLogs", deletedAuditLogs);

        long deletedLoginLogs = loginLogMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreatedAt, cutoff));
        result.put("deletedLoginLogs", deletedLoginLogs);

        return ApiResult.ok(result);
    }
}
