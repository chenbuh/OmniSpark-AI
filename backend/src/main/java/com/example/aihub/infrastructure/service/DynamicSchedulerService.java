package com.example.aihub.infrastructure.service;

import com.example.aihub.infrastructure.entity.ScheduledTask;
import com.example.aihub.infrastructure.mapper.ScheduledTaskMapper;
import com.example.aihub.module.system.AdminCleanupController;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.PagingUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicSchedulerService {
    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of("cleanup");
    private static final int CLEANUP_MIN_DAYS_OLD = 7;

    private final ScheduledTaskMapper taskMapper;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
    private final AdminCleanupController cleanupController;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        // 每分钟检查一次，由真实 Cron 表达式决定是否命中执行窗口
        executor.scheduleAtFixedRate(this::checkAndRun, 1, 1, TimeUnit.MINUTES);
        log.info("Dynamic scheduler initialized");
    }

    public List<ScheduledTask> list(int limit) {
        return taskMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScheduledTask>()
                .orderByDesc(ScheduledTask::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduledTask toggle(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("定时任务不存在");
        }
        task.setEnabled(task.getEnabled() == 1 ? 0 : 1);
        taskMapper.updateById(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduledTask create(ScheduledTask task) {
        validateTask(task);
        task.setEnabled(task.getEnabled() != null ? task.getEnabled() : 1);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduledTask update(ScheduledTask task) {
        ScheduledTask existing = taskMapper.selectById(task.getId());
        if (existing == null) {
            throw new BusinessException("定时任务不存在");
        }
        validateTask(task);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return taskMapper.selectById(task.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("定时任务不存在");
        }
        taskMapper.deleteById(id);
    }

    public void runNow(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("定时任务不存在");
        }
        validateTask(task);
        executeTask(task);
    }

    private void checkAndRun() {
        List<ScheduledTask> tasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScheduledTask>()
                        .eq(ScheduledTask::getEnabled, 1));
        for (ScheduledTask task : tasks) {
            if (!isSupportedTaskType(task.getTaskType())) {
                log.warn("Skipping unsupported scheduled task type: id={}, name={}, type={}", task.getId(), task.getName(), task.getTaskType());
                continue;
            }
            if (shouldRun(task)) executeTask(task);
        }
    }

    private boolean shouldRun(ScheduledTask task) {
        String cron = task.getCron();
        if (cron == null || cron.isBlank()) return false;
        CronExpression cronExpression;
        try {
            cronExpression = CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Skipping invalid cron expression: id={}, name={}, cron={}", task.getId(), task.getName(), cron);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime referenceTime = task.getLastRunAt();
        if (referenceTime == null) {
            referenceTime = task.getUpdatedAt() != null ? task.getUpdatedAt() : task.getCreatedAt();
        }
        if (referenceTime == null) {
            referenceTime = now.minusMinutes(1);
        }
        LocalDateTime nextRun = cronExpression.next(referenceTime.minusSeconds(1));
        return nextRun != null && !nextRun.isAfter(now);
    }

    private void executeTask(ScheduledTask task) {
        try {
            task.setLastRunAt(LocalDateTime.now());
            task.setLastStatus("running");
            taskMapper.updateById(task);

            switch (task.getTaskType()) {
                case "cleanup" -> {
                    int days = resolveCleanupDaysOld(task.getConfigJson());
                    cleanupController.execute(days);
                    log.info("Scheduled cleanup completed: {} days", days);
                }
                default -> throw new IllegalArgumentException("暂不支持的任务类型: " + task.getTaskType());
            }

            task.setLastStatus("success");
        } catch (Exception e) {
            task.setLastStatus("failed");
            log.error("Scheduled task '{}' failed: {}", task.getName(), e.getMessage());
        }
        taskMapper.updateById(task);
    }

    private void validateTask(ScheduledTask task) {
        if (task == null) {
            throw new BusinessException("任务不能为空");
        }
        String cron = task.getCron() == null ? "" : task.getCron().trim();
        if (cron.isBlank()) {
            throw new BusinessException("Cron 表达式不能为空");
        }
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Cron 表达式无效，请填写真实可执行的调度规则");
        }
        task.setCron(cron);
        String normalizedType = normalizeTaskType(task.getTaskType());
        if (!isSupportedTaskType(normalizedType)) {
            throw new BusinessException("当前仅支持真实可执行的“数据清理”任务类型");
        }
        task.setTaskType(normalizedType);
        if ("cleanup".equals(normalizedType)) {
            int daysOld = resolveCleanupDaysOld(task.getConfigJson());
            task.setConfigJson("{\"daysOld\":" + daysOld + "}");
        }
    }

    private boolean isSupportedTaskType(String taskType) {
        return SUPPORTED_TASK_TYPES.contains(normalizeTaskType(taskType));
    }

    private String normalizeTaskType(String taskType) {
        return taskType == null ? "" : taskType.trim().toLowerCase(Locale.ROOT);
    }

    private int resolveCleanupDaysOld(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return 30;
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            int daysOld = node.path("daysOld").asInt(30);
            if (daysOld < CLEANUP_MIN_DAYS_OLD) {
                throw new BusinessException("数据清理任务的保留天数不能小于 " + CLEANUP_MIN_DAYS_OLD + " 天");
            }
            return daysOld;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("数据清理任务配置无效，请填写真实可执行的保留天数");
        }
    }
}
