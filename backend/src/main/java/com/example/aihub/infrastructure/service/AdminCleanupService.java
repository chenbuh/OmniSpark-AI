package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.AuditLog;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.entity.LoginLog;
import com.example.aihub.infrastructure.entity.QuotaRecord;
import com.example.aihub.infrastructure.entity.StyleCard;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.mapper.AuditLogMapper;
import com.example.aihub.infrastructure.mapper.GenerationTaskMapper;
import com.example.aihub.infrastructure.mapper.LoginLogMapper;
import com.example.aihub.infrastructure.mapper.QuotaRecordMapper;
import com.example.aihub.infrastructure.mapper.StyleCardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminCleanupService {
    private static final int MIN_DAYS_OLD = 7;
    private static final String CLEANUP_SCOPE = "task-asset-log-cleanup";
    private static final String CLEANUP_MESSAGE = "当前仅清理旧任务、旧资产、审计日志、登录日志，并同步删除关联资产文件；不处理用户、项目、系统配置或外部存储数据";

    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final AuditLogMapper auditLogMapper;
    private final LoginLogMapper loginLogMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final StyleCardMapper styleCardMapper;
    private final AssetService assetService;
    private final SubtitleService subtitleService;

    public Map<String, Object> preview(int daysOld) {
        int safeDaysOld = requireValidDaysOld(daysOld);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDaysOld);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", CLEANUP_SCOPE);
        result.put("message", CLEANUP_MESSAGE);
        result.put("cleanupTargets", List.of("generation_task", "asset", "audit_log", "login_log", "asset-files"));
        result.put("daysOld", safeDaysOld);
        result.put("cutoffDate", cutoff.toString());
        result.put("oldTasks", countTasksBefore(cutoff));
        result.put("oldAssets", countAssetsBefore(cutoff));
        result.put("oldAuditLogs", countAuditLogsBefore(cutoff));
        result.put("oldLoginLogs", countLoginLogsBefore(cutoff));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> execute(int daysOld) {
        int safeDaysOld = requireValidDaysOld(daysOld);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeDaysOld);

        List<GenerationTask> oldTasks = taskMapper.selectList(
                new LambdaQueryWrapper<GenerationTask>()
                        .lt(GenerationTask::getCreatedAt, cutoff)
                        .select(GenerationTask::getId));
        List<Asset> oldAssets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>()
                        .lt(Asset::getCreatedAt, cutoff));

        List<Long> oldTaskIds = oldTasks.stream().map(GenerationTask::getId).toList();
        List<Long> oldAssetIds = oldAssets.stream().map(Asset::getId).toList();

        if (!oldTaskIds.isEmpty()) {
            assetMapper.update(null, new LambdaUpdateWrapper<Asset>()
                    .in(Asset::getTaskId, oldTaskIds)
                    .set(Asset::getTaskId, null));
            quotaRecordMapper.update(null, new LambdaUpdateWrapper<QuotaRecord>()
                    .in(QuotaRecord::getTaskId, oldTaskIds)
                    .set(QuotaRecord::getTaskId, null));
            taskMapper.delete(new LambdaQueryWrapper<GenerationTask>().in(GenerationTask::getId, oldTaskIds));
        }

        if (!oldAssetIds.isEmpty()) {
            subtitleService.deleteByAssetIds(oldAssetIds);
            styleCardMapper.update(null, new LambdaUpdateWrapper<StyleCard>()
                    .in(StyleCard::getRefAssetId, oldAssetIds)
                    .set(StyleCard::getRefAssetId, null));

            Set<String> filesToDelete = new LinkedHashSet<>();
            for (Asset asset : oldAssets) {
                if (asset.getFileUrl() != null && !asset.getFileUrl().isBlank()) {
                    filesToDelete.add(asset.getFileUrl());
                }
                if (asset.getThumbUrl() != null && !asset.getThumbUrl().isBlank()) {
                    filesToDelete.add(asset.getThumbUrl());
                }
            }

            assetMapper.delete(new LambdaQueryWrapper<Asset>().in(Asset::getId, oldAssetIds));
            filesToDelete.forEach(assetService::deleteAssetFile);
        }

        long deletedAuditLogs = auditLogMapper.delete(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
        long deletedLoginLogs = loginLogMapper.delete(new LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreatedAt, cutoff));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", CLEANUP_SCOPE);
        result.put("message", CLEANUP_MESSAGE);
        result.put("cleanupTargets", List.of("generation_task", "asset", "audit_log", "login_log", "asset-files"));
        result.put("daysOld", safeDaysOld);
        result.put("cutoffDate", cutoff.toString());
        result.put("deletedTasks", oldTaskIds.size());
        result.put("deletedAssets", oldAssetIds.size());
        result.put("deletedAuditLogs", deletedAuditLogs);
        result.put("deletedLoginLogs", deletedLoginLogs);
        return result;
    }

    private long countTasksBefore(LocalDateTime cutoff) {
        Long count = taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .lt(GenerationTask::getCreatedAt, cutoff));
        return count == null ? 0L : count;
    }

    private long countAssetsBefore(LocalDateTime cutoff) {
        Long count = assetMapper.selectCount(new LambdaQueryWrapper<Asset>()
                .lt(Asset::getCreatedAt, cutoff));
        return count == null ? 0L : count;
    }

    private long countAuditLogsBefore(LocalDateTime cutoff) {
        Long count = auditLogMapper.selectCount(new LambdaQueryWrapper<AuditLog>()
                .lt(AuditLog::getCreatedAt, cutoff));
        return count == null ? 0L : count;
    }

    private long countLoginLogsBefore(LocalDateTime cutoff) {
        Long count = loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreatedAt, cutoff));
        return count == null ? 0L : count;
    }

    private int requireValidDaysOld(int daysOld) {
        if (daysOld < MIN_DAYS_OLD) {
            throw new BusinessException("保留天数不能小于 " + MIN_DAYS_OLD + " 天，以防误删近期数据");
        }
        return daysOld;
    }
}
