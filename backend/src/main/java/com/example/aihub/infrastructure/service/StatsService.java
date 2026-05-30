package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.entity.Project;
import com.example.aihub.infrastructure.entity.QuotaRecord;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.mapper.GenerationTaskMapper;
import com.example.aihub.infrastructure.mapper.ProjectMapper;
import com.example.aihub.infrastructure.mapper.QuotaRecordMapper;
import com.example.aihub.infrastructure.vo.StatsActivityVO;
import com.example.aihub.infrastructure.vo.StatsDashboardVO;
import com.example.aihub.infrastructure.vo.StatsDistributionVO;
import com.example.aihub.infrastructure.vo.StatsOverviewVO;
import com.example.aihub.infrastructure.vo.StatsProjectRankingVO;
import com.example.aihub.infrastructure.vo.StatsTrendPointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class StatsService {
    private final ProjectMapper projectMapper;
    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final QuotaService quotaService;

    public StatsOverviewVO overview(Long projectId) {
        StatsScope scope = loadScope(projectId);
        return buildOverview(scope);
    }

    public StatsDashboardVO dashboard(Long projectId) {
        StatsScope scope = loadScope(projectId);
        StatsDashboardVO vo = new StatsDashboardVO();
        vo.setOverview(buildOverview(scope));
        vo.setDistribution(buildDistribution(scope.tasks));
        vo.setTrends(buildTrends(scope.tasks, scope.quotaRecords));
        vo.setProjectRankings(buildProjectRankings(scope.projects, scope.tasks, scope.assets, scope.quotaRecords));
        vo.setRecentActivities(buildActivities(scope.projects, scope.tasks, scope.quotaRecords));
        return vo;
    }

    private StatsOverviewVO buildOverview(StatsScope scope) {
        StatsOverviewVO vo = new StatsOverviewVO();
        long successTaskCount = scope.tasks.stream().filter(task -> "success".equals(task.getStatus())).count();
        int quotaUsed = scope.quotaRecords.stream().mapToInt(item -> item.getAmount() == null ? 0 : item.getAmount()).sum();
        var quotaSummary = quotaService.summary();

        vo.setProjectCount((long) scope.projects.size());
        vo.setTaskCount((long) scope.tasks.size());
        vo.setSuccessTaskCount(successTaskCount);
        vo.setAssetCount((long) scope.assets.size());
        vo.setFavoriteAssetCount(scope.assets.stream().filter(asset -> Integer.valueOf(1).equals(asset.getFavorite())).count());
        vo.setQuotaUsed(quotaUsed);
        vo.setQuotaLimit(quotaSummary.getQuotaLimit());
        return vo;
    }

    private StatsDistributionVO buildDistribution(List<GenerationTask> tasks) {
        StatsDistributionVO vo = new StatsDistributionVO();
        vo.setImageTaskCount(tasks.stream().filter(task -> "image".equals(task.getTaskType())).count());
        vo.setVideoTaskCount(tasks.stream().filter(task -> "video".equals(task.getTaskType())).count());
        vo.setSuccessTaskCount(tasks.stream().filter(task -> "success".equals(task.getStatus())).count());
        vo.setRunningTaskCount(tasks.stream().filter(task -> "running".equals(task.getStatus()) || "pending".equals(task.getStatus())).count());
        vo.setFailedTaskCount(tasks.stream().filter(task -> "failed".equals(task.getStatus())).count());
        return vo;
    }

    private List<StatsTrendPointVO> buildTrends(List<GenerationTask> tasks, List<QuotaRecord> quotaRecords) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        Map<LocalDate, Long> taskCountMap = new LinkedHashMap<>();
        Map<LocalDate, Integer> quotaMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            taskCountMap.put(day, 0L);
            quotaMap.put(day, 0);
        }

        for (GenerationTask task : tasks) {
            if (task.getCreatedAt() == null) continue;
            LocalDate day = task.getCreatedAt().toLocalDate();
            if (taskCountMap.containsKey(day)) {
                taskCountMap.put(day, taskCountMap.get(day) + 1);
            }
        }

        for (QuotaRecord record : quotaRecords) {
            if (record.getCreatedAt() == null) continue;
            LocalDate day = record.getCreatedAt().toLocalDate();
            if (quotaMap.containsKey(day)) {
                quotaMap.put(day, quotaMap.get(day) + (record.getAmount() == null ? 0 : record.getAmount()));
            }
        }

        List<StatsTrendPointVO> result = new ArrayList<>();
        for (LocalDate day : taskCountMap.keySet()) {
            StatsTrendPointVO item = new StatsTrendPointVO();
            item.setDate(day.format(formatter));
            item.setTaskCount(taskCountMap.get(day));
            item.setQuotaUsed(quotaMap.get(day));
            result.add(item);
        }
        return result;
    }

    private List<StatsProjectRankingVO> buildProjectRankings(List<Project> projects,
                                                             List<GenerationTask> tasks,
                                                             List<Asset> assets,
                                                             List<QuotaRecord> quotaRecords) {
        List<StatsProjectRankingVO> rankings = new ArrayList<>();
        int maxScore = 1;

        for (Project project : projects) {
            List<GenerationTask> projectTasks = tasks.stream()
                    .filter(task -> Objects.equals(task.getProjectId(), project.getId()))
                    .toList();
            List<Asset> projectAssets = assets.stream()
                    .filter(asset -> Objects.equals(asset.getProjectId(), project.getId()))
                    .toList();
            List<QuotaRecord> projectQuota = quotaRecords.stream()
                    .filter(record -> Objects.equals(record.getProjectId(), project.getId()))
                    .toList();

            long successCount = projectTasks.stream().filter(task -> "success".equals(task.getStatus())).count();
            int quotaUsed = projectQuota.stream().mapToInt(item -> item.getAmount() == null ? 0 : item.getAmount()).sum();
            int score = projectTasks.size() * 10 + (int) successCount * 6 + quotaUsed;
            maxScore = Math.max(maxScore, score);

            StatsProjectRankingVO item = new StatsProjectRankingVO();
            item.setProjectId(project.getId());
            item.setName(project.getName());
            item.setDescription(project.getDescription());
            item.setTaskCount((long) projectTasks.size());
            item.setSuccessTaskCount(successCount);
            item.setSuccessRate(projectTasks.isEmpty() ? 0D : (successCount * 100D) / projectTasks.size());
            item.setAssetCount((long) projectAssets.size());
            item.setQuotaUsed(quotaUsed);
            item.setLastActiveAt(projectTasks.stream()
                    .map(GenerationTask::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
            item.setWeightPercent((double) score);
            rankings.add(item);
        }

        rankings.sort(Comparator.comparing(StatsProjectRankingVO::getWeightPercent).reversed());
        for (int i = 0; i < rankings.size(); i++) {
            StatsProjectRankingVO item = rankings.get(i);
            item.setRank(i + 1);
            item.setWeightPercent((item.getWeightPercent() / maxScore) * 100D);
        }
        return rankings;
    }

    private List<StatsActivityVO> buildActivities(List<Project> projects, List<GenerationTask> tasks, List<QuotaRecord> quotaRecords) {
        Map<Long, String> projectNameMap = new LinkedHashMap<>();
        for (Project project : projects) {
            projectNameMap.put(project.getId(), project.getName());
        }

        List<StatsActivityVO> items = new ArrayList<>();
        for (GenerationTask task : tasks) {
            StatsActivityVO item = new StatsActivityVO();
            item.setType("video".equals(task.getTaskType()) ? "video" : ("failed".equals(task.getStatus()) ? "error" : "image"));
            item.setTitle(("video".equals(task.getTaskType()) ? "视频" : "图像") + "任务 #" + task.getId());
            item.setDescription(String.format("%s · %s · %s",
                    statusLabel(task.getStatus()),
                    projectNameMap.getOrDefault(task.getProjectId(), "未知项目"),
                    firstNonBlank(task.getModelName(), "默认模型")));
            item.setStatus(task.getStatus());
            item.setCreatedAt(task.getCreatedAt());
            items.add(item);
        }

        for (QuotaRecord record : quotaRecords) {
            StatsActivityVO item = new StatsActivityVO();
            item.setType("quota");
            item.setTitle("额度消耗 " + (record.getAmount() == null ? 0 : record.getAmount()));
            item.setDescription(String.format("%s · %s%s",
                    projectNameMap.getOrDefault(record.getProjectId(), "未知项目"),
                    firstNonBlank(record.getQuotaType(), "generation"),
                    record.getRemark() != null && !record.getRemark().isBlank() ? " · " + record.getRemark() : ""));
            item.setStatus("success");
            item.setCreatedAt(record.getCreatedAt());
            items.add(item);
        }

        return items.stream()
                .sorted(Comparator.comparing(StatsActivityVO::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
                .limit(8)
                .toList();
    }

    private StatsScope loadScope(Long projectId) {
        Long userId = SecurityUtil.loginUserId();
        LambdaQueryWrapper<Project> projectWrapper = new LambdaQueryWrapper<Project>().eq(Project::getUserId, userId);
        if (projectId != null) {
            projectWrapper.eq(Project::getId, projectId);
        }

        List<Project> projects = projectMapper.selectList(projectWrapper);
        if (projectId != null && projects.isEmpty()) {
            throw new BusinessException("项目不存在或无权访问");
        }

        List<Long> projectIds = new ArrayList<>(projects.stream().map(Project::getId).toList());
        // 始终包含 projectId=0（无项目的默认空间）
        if (!projectIds.contains(0L)) {
            projectIds.add(0L);
        }
        if (projectIds.isEmpty()) {
            return new StatsScope(projects, List.of(), List.of(), List.of());
        }

        List<GenerationTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getProjectId, projectIds)
                .orderByDesc(GenerationTask::getCreatedAt));
        List<Asset> assets = assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .in(Asset::getProjectId, projectIds)
                .orderByDesc(Asset::getCreatedAt));
        List<QuotaRecord> quotaRecords = quotaRecordMapper.selectList(new LambdaQueryWrapper<QuotaRecord>()
                .eq(QuotaRecord::getUserId, userId)
                .in(QuotaRecord::getProjectId, projectIds)
                .orderByDesc(QuotaRecord::getCreatedAt));

        return new StatsScope(projects, tasks, assets, quotaRecords);
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "success" -> "已完成";
            case "failed" -> "执行失败";
            case "running", "pending" -> "正在运行";
            default -> "状态未知";
        };
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record StatsScope(
            List<Project> projects,
            List<GenerationTask> tasks,
            List<Asset> assets,
            List<QuotaRecord> quotaRecords
    ) {
    }
}
