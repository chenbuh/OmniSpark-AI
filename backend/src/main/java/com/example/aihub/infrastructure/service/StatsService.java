package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.security.ProjectAccessGuard;
import com.example.aihub.common.util.SecurityUtil;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatsService {
    private final ProjectMapper projectMapper;
    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final QuotaService quotaService;
    private final ProjectAccessGuard projectAccessGuard;

    public StatsOverviewVO overview(Long projectId) {
        StatsScope scope = loadScope(projectId);
        return buildOverview(scope, loadProjectMetrics(scope.projectIds(), scope.userId()));
    }

    public StatsDashboardVO dashboard(Long projectId) {
        StatsScope scope = loadScope(projectId);
        ProjectMetrics metrics = loadProjectMetrics(scope.projectIds(), scope.userId());

        StatsDashboardVO vo = new StatsDashboardVO();
        vo.setOverview(buildOverview(scope, metrics));
        vo.setDistribution(buildDistribution(metrics));
        vo.setTrends(buildTrends(metrics));
        vo.setProjectRankings(buildProjectRankings(scope.projects(), metrics));
        vo.setRecentActivities(buildActivities(scope));
        return vo;
    }

    private StatsOverviewVO buildOverview(StatsScope scope, ProjectMetrics metrics) {
        StatsOverviewVO vo = new StatsOverviewVO();
        var quotaSummary = quotaService.summary();

        vo.setProjectCount((long) scope.projects().size());
        vo.setTaskCount(metrics.taskCount());
        vo.setSuccessTaskCount(metrics.successTaskCount());
        vo.setAssetCount(metrics.assetCount());
        vo.setFavoriteAssetCount(metrics.favoriteAssetCount());
        vo.setQuotaUsed(metrics.quotaUsed());
        vo.setQuotaLimit(quotaSummary.getQuotaLimit());
        return vo;
    }

    private StatsDistributionVO buildDistribution(ProjectMetrics metrics) {
        StatsDistributionVO vo = new StatsDistributionVO();
        vo.setImageTaskCount(metrics.imageTaskCount());
        vo.setVideoTaskCount(metrics.videoTaskCount());
        vo.setSuccessTaskCount(metrics.successTaskCount());
        vo.setRunningTaskCount(metrics.runningTaskCount());
        vo.setFailedTaskCount(metrics.failedTaskCount());
        return vo;
    }

    private List<StatsTrendPointVO> buildTrends(ProjectMetrics metrics) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        Map<LocalDate, Long> taskCountMap = new LinkedHashMap<>();
        Map<LocalDate, Integer> quotaMap = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            taskCountMap.put(day, metrics.dailyTaskCount().getOrDefault(day, 0L));
            quotaMap.put(day, metrics.dailyQuotaUsed().getOrDefault(day, 0));
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

    private List<StatsProjectRankingVO> buildProjectRankings(List<Project> projects, ProjectMetrics metrics) {
        List<StatsProjectRankingVO> rankings = new ArrayList<>();
        int maxScore = 1;

        for (Project project : projects) {
            long taskCount = metrics.taskCountByProject().getOrDefault(project.getId(), 0L);
            long successCount = metrics.successTaskCountByProject().getOrDefault(project.getId(), 0L);
            long assetCount = metrics.assetCountByProject().getOrDefault(project.getId(), 0L);
            int quotaUsed = metrics.quotaUsedByProject().getOrDefault(project.getId(), 0);
            int score = (int) taskCount * 10 + (int) successCount * 6 + quotaUsed;
            maxScore = Math.max(maxScore, score);

            StatsProjectRankingVO item = new StatsProjectRankingVO();
            item.setProjectId(project.getId());
            item.setName(project.getName());
            item.setDescription(project.getDescription());
            item.setTaskCount(taskCount);
            item.setSuccessTaskCount(successCount);
            item.setSuccessRate(taskCount == 0 ? 0D : (successCount * 100D) / taskCount);
            item.setAssetCount(assetCount);
            item.setQuotaUsed(quotaUsed);
            item.setLastActiveAt(metrics.lastActiveAtByProject().get(project.getId()));
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

    private List<StatsActivityVO> buildActivities(StatsScope scope) {
        Map<Long, String> projectNameMap = new LinkedHashMap<>();
        for (Project project : scope.projects()) {
            projectNameMap.put(project.getId(), project.getName());
        }

        List<GenerationTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getProjectId, scope.projectIds())
                .orderByDesc(GenerationTask::getCreatedAt)
                .last("LIMIT 8"));
        List<QuotaRecord> quotaRecords = quotaRecordMapper.selectList(new LambdaQueryWrapper<QuotaRecord>()
                .eq(QuotaRecord::getUserId, scope.userId())
                .in(QuotaRecord::getProjectId, scope.projectIds())
                .orderByDesc(QuotaRecord::getCreatedAt)
                .last("LIMIT 8"));

        List<StatsActivityVO> items = new ArrayList<>();
        for (GenerationTask task : tasks) {
            StatsActivityVO item = new StatsActivityVO();
            item.setType("video".equals(task.getTaskType()) ? "video" : ("failed".equals(task.getStatus()) ? "error" : "image"));
            item.setTitle(("video".equals(task.getTaskType()) ? "视频" : "图像") + "任务 #" + task.getId());
            item.setDescription(joinNonBlank(
                    statusLabel(task.getStatus()),
                    describeProject(task.getProjectId(), projectNameMap),
                    task.getModelName()));
            item.setStatus(task.getStatus());
            item.setCreatedAt(task.getCreatedAt());
            items.add(item);
        }

        for (QuotaRecord record : quotaRecords) {
            StatsActivityVO item = new StatsActivityVO();
            item.setType("quota");
            item.setTitle("额度消耗 " + (record.getAmount() == null ? 0 : record.getAmount()));
            item.setDescription(String.format("%s · %s%s",
                    describeProject(record.getProjectId(), projectNameMap),
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
        List<Project> projects;
        if (projectId != null) {
            projectAccessGuard.assertAccess(projectId);
            Project project = projectMapper.selectById(projectId);
            if (project == null) {
                throw new BusinessException("项目不存在或无权访问");
            }
            projects = List.of(project);
        } else {
            List<Long> accessibleProjectIds = projectAccessGuard.accessibleProjectIds();
            if (accessibleProjectIds.isEmpty()) {
                projects = List.of();
            } else {
                projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                        .in(Project::getId, accessibleProjectIds)
                        .orderByDesc(Project::getId));
            }
        }

        List<Long> projectIds = new ArrayList<>(projects.stream().map(Project::getId).toList());
        if (!projectIds.contains(0L)) {
            projectIds.add(0L);
        }
        return new StatsScope(userId, projects, projectIds);
    }

    private ProjectMetrics loadProjectMetrics(List<Long> projectIds, Long userId) {
        if (projectIds.isEmpty()) {
            return ProjectMetrics.empty();
        }

        long taskCount = defaultLong(taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getProjectId, projectIds)));
        long successTaskCount = defaultLong(taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getProjectId, projectIds)
                .eq(GenerationTask::getStatus, "success")));
        long failedTaskCount = defaultLong(taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getProjectId, projectIds)
                .eq(GenerationTask::getStatus, "failed")));
        long runningTaskCount = defaultLong(taskMapper.selectCount(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getProjectId, projectIds)
                .and(w -> w.eq(GenerationTask::getStatus, "running").or().eq(GenerationTask::getStatus, "pending"))));
        long assetCount = defaultLong(assetMapper.selectCount(new LambdaQueryWrapper<com.example.aihub.infrastructure.entity.Asset>()
                .in(com.example.aihub.infrastructure.entity.Asset::getProjectId, projectIds)));
        long favoriteAssetCount = defaultLong(assetMapper.selectCount(new LambdaQueryWrapper<com.example.aihub.infrastructure.entity.Asset>()
                .in(com.example.aihub.infrastructure.entity.Asset::getProjectId, projectIds)
                .eq(com.example.aihub.infrastructure.entity.Asset::getFavorite, 1)));
        int quotaUsed = sumQuota(projectIds, userId);

        Map<String, Long> taskTypeCount = mapLongByKey(taskMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenerationTask>()
                        .select("task_type AS metricKey", "COUNT(*) AS metricValue")
                        .in("project_id", projectIds)
                        .groupBy("task_type")), "metricKey", "metricValue");
        Map<Long, Long> taskCountByProject = mapLongByLongKey(taskMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenerationTask>()
                        .select("project_id AS metricKey", "COUNT(*) AS metricValue")
                        .in("project_id", projectIds)
                        .groupBy("project_id")), "metricKey", "metricValue");
        Map<Long, Long> successTaskCountByProject = mapLongByLongKey(taskMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenerationTask>()
                        .select("project_id AS metricKey", "COUNT(*) AS metricValue")
                        .in("project_id", projectIds)
                        .eq("status", "success")
                        .groupBy("project_id")), "metricKey", "metricValue");
        Map<Long, LocalDateTime> lastActiveAtByProject = mapDateTimeByLongKey(taskMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenerationTask>()
                        .select("project_id AS metricKey", "MAX(created_at) AS metricValue")
                        .in("project_id", projectIds)
                        .groupBy("project_id")), "metricKey", "metricValue");
        Map<Long, Long> assetCountByProject = mapLongByLongKey(assetMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.example.aihub.infrastructure.entity.Asset>()
                        .select("project_id AS metricKey", "COUNT(*) AS metricValue")
                        .in("project_id", projectIds)
                        .groupBy("project_id")), "metricKey", "metricValue");
        Map<Long, Integer> quotaUsedByProject = mapIntByLongKey(quotaRecordMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QuotaRecord>()
                        .select("project_id AS metricKey", "COALESCE(SUM(amount), 0) AS metricValue")
                        .eq("user_id", userId)
                        .in("project_id", projectIds)
                        .groupBy("project_id")), "metricKey", "metricValue");

        LocalDate startDay = LocalDate.now().minusDays(13);
        String startTime = startDay.atStartOfDay().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<LocalDate, Long> dailyTaskCount = mapLongByDateKey(taskMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenerationTask>()
                        .select("DATE(created_at) AS metricKey", "COUNT(*) AS metricValue")
                        .in("project_id", projectIds)
                        .ge("created_at", startTime)
                        .groupBy("DATE(created_at)")), "metricKey", "metricValue");
        Map<LocalDate, Integer> dailyQuotaUsed = mapIntByDateKey(quotaRecordMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QuotaRecord>()
                        .select("DATE(created_at) AS metricKey", "COALESCE(SUM(amount), 0) AS metricValue")
                        .eq("user_id", userId)
                        .in("project_id", projectIds)
                        .ge("created_at", startTime)
                        .groupBy("DATE(created_at)")), "metricKey", "metricValue");

        return new ProjectMetrics(
                taskCount,
                successTaskCount,
                failedTaskCount,
                runningTaskCount,
                assetCount,
                favoriteAssetCount,
                quotaUsed,
                taskTypeCount.getOrDefault("image", 0L),
                taskTypeCount.getOrDefault("video", 0L),
                dailyTaskCount,
                dailyQuotaUsed,
                taskCountByProject,
                successTaskCountByProject,
                assetCountByProject,
                quotaUsedByProject,
                lastActiveAtByProject
        );
    }

    private int sumQuota(List<Long> projectIds, Long userId) {
        List<Map<String, Object>> rows = quotaRecordMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<QuotaRecord>()
                        .select("COALESCE(SUM(amount), 0) AS metricValue")
                        .eq("user_id", userId)
                        .in("project_id", projectIds));
        if (rows.isEmpty()) {
            return 0;
        }
        return asInt(rows.get(0).get("metricValue"));
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    private Map<String, Long> mapLongByKey(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            if (key != null) {
                result.put(String.valueOf(key), asLong(row.get(valueField)));
            }
        }
        return result;
    }

    private Map<Long, Long> mapLongByLongKey(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            if (key != null) {
                result.put(asLong(key), asLong(row.get(valueField)));
            }
        }
        return result;
    }

    private Map<Long, Integer> mapIntByLongKey(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<Long, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            if (key != null) {
                result.put(asLong(key), asInt(row.get(valueField)));
            }
        }
        return result;
    }

    private Map<Long, LocalDateTime> mapDateTimeByLongKey(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            if (key != null) {
                LocalDateTime value = asLocalDateTime(row.get(valueField));
                if (value != null) {
                    result.put(asLong(key), value);
                }
            }
        }
        return result;
    }

    private Map<LocalDate, Long> mapLongByDateKey(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            if (key != null) {
                result.put(LocalDate.parse(String.valueOf(key)), asLong(row.get(valueField)));
            }
        }
        return result;
    }

    private Map<LocalDate, Integer> mapIntByDateKey(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<LocalDate, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object key = row.get(keyField);
            if (key != null) {
                result.put(LocalDate.parse(String.valueOf(key)), asInt(row.get(valueField)));
            }
        }
        return result;
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }

    private int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
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

    private String joinNonBlank(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private String describeProject(Long projectId, Map<Long, String> projectNameMap) {
        String projectName = projectId == null ? null : projectNameMap.get(projectId);
        if (projectName != null && !projectName.isBlank()) {
            return projectName;
        }
        if (projectId == null) {
            return "项目待确认";
        }
        if (projectId == 0L) {
            return "公共资源池";
        }
        return "项目已删除 (#" + projectId + ")";
    }

    private record StatsScope(
            Long userId,
            List<Project> projects,
            List<Long> projectIds
    ) {
    }

    private record ProjectMetrics(
            long taskCount,
            long successTaskCount,
            long failedTaskCount,
            long runningTaskCount,
            long assetCount,
            long favoriteAssetCount,
            int quotaUsed,
            long imageTaskCount,
            long videoTaskCount,
            Map<LocalDate, Long> dailyTaskCount,
            Map<LocalDate, Integer> dailyQuotaUsed,
            Map<Long, Long> taskCountByProject,
            Map<Long, Long> successTaskCountByProject,
            Map<Long, Long> assetCountByProject,
            Map<Long, Integer> quotaUsedByProject,
            Map<Long, LocalDateTime> lastActiveAtByProject
    ) {
        private static ProjectMetrics empty() {
            return new ProjectMetrics(
                    0L, 0L, 0L, 0L, 0L, 0L, 0,
                    0L, 0L,
                    Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()
            );
        }
    }
}
