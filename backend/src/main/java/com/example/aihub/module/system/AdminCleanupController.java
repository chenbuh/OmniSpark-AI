package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.AuditLog;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
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
    /** 清理保留天数下限，防止传入过小或负数导致误删近期/全量数据。 */
    private static final int MIN_DAYS_OLD = 7;

    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final AuditLogMapper auditLogMapper;
    private final LoginLogMapper loginLogMapper;

    @GetMapping("/preview")
    public ApiResult<Map<String, Object>> preview(@RequestParam(defaultValue = "30") int daysOld) {
        daysOld = requireValidDaysOld(daysOld);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("daysOld", daysOld);
        result.put("cutoffDate", cutoff.toString());

        long oldTasks = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .lt(GenerationTask::getCreatedAt, cutoff));
        result.put("oldTasks", oldTasks);

        long oldAssets = assetMapper.selectCount(new LambdaQueryWrapper<Asset>()
                .lt(Asset::getCreatedAt, cutoff));
        result.put("oldAssets", oldAssets);

        long oldAuditLogs = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
        result.put("oldAuditLogs", oldAuditLogs);

        long oldLoginLogs = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreatedAt, cutoff));
        result.put("oldLoginLogs", oldLoginLogs);

        return ApiResult.ok(result);
    }

    @DeleteMapping("/execute")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Map<String, Object>> execute(@RequestParam(defaultValue = "30") int daysOld) {
        daysOld = requireValidDaysOld(daysOld);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cutoffDate", cutoff.toString());

        long deletedTasks = taskMapper.delete(new LambdaQueryWrapper<GenerationTask>()
                .lt(GenerationTask::getCreatedAt, cutoff));
        result.put("deletedTasks", deletedTasks);

        long deletedAssets = assetMapper.delete(new LambdaQueryWrapper<Asset>()
                .lt(Asset::getCreatedAt, cutoff));
        result.put("deletedAssets", deletedAssets);

        long deletedAuditLogs = auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
        result.put("deletedAuditLogs", deletedAuditLogs);

        long deletedLoginLogs = loginLogMapper.delete(new LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreatedAt, cutoff));
        result.put("deletedLoginLogs", deletedLoginLogs);

        return ApiResult.ok(result);
    }

    /** 校验保留天数不低于下限，拒绝 0 / 负数等可能清空全库的取值。 */
    private int requireValidDaysOld(int daysOld) {
        if (daysOld < MIN_DAYS_OLD) {
            throw new BusinessException("保留天数不能小于 " + MIN_DAYS_OLD + " 天，以防误删近期数据");
        }
        return daysOld;
    }
}
