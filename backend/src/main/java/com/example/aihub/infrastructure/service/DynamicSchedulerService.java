package com.example.aihub.infrastructure.service;

import com.example.aihub.infrastructure.entity.ScheduledTask;
import com.example.aihub.infrastructure.mapper.ScheduledTaskMapper;
import com.example.aihub.module.system.AdminCleanupController;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicSchedulerService {
    private final ScheduledTaskMapper taskMapper;
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
    private final AdminCleanupController cleanupController;

    @PostConstruct
    public void init() {
        // 每小时检查一次是否有需要执行的任务
        executor.scheduleAtFixedRate(this::checkAndRun, 1, 1, TimeUnit.HOURS);
        log.info("Dynamic scheduler initialized");
    }

    public List<ScheduledTask> list() {
        return taskMapper.selectList(null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduledTask toggle(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task == null) return null;
        task.setEnabled(task.getEnabled() == 1 ? 0 : 1);
        taskMapper.updateById(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduledTask create(ScheduledTask task) {
        task.setEnabled(task.getEnabled() != null ? task.getEnabled() : 1);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public ScheduledTask update(ScheduledTask task) {
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return taskMapper.selectById(task.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        taskMapper.deleteById(id);
    }

    public void runNow(Long id) {
        ScheduledTask task = taskMapper.selectById(id);
        if (task != null) executeTask(task);
    }

    private void checkAndRun() {
        List<ScheduledTask> tasks = taskMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScheduledTask>()
                        .eq(ScheduledTask::getEnabled, 1));
        for (ScheduledTask task : tasks) {
            if (shouldRun(task)) executeTask(task);
        }
    }

    private boolean shouldRun(ScheduledTask task) {
        String cron = task.getCron();
        // 简化 cron 解析：支持 "0 0 3 * * ?" (每天3点), "0 0 * * * ?" (每小时)
        if (cron == null) return false;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastRun = task.getLastRunAt();

        if (lastRun == null) return true;

        String[] parts = cron.trim().split("\\s+");
        if (parts.length >= 3) {
            String minute = parts[1]; // 分钟
            String hour = parts[2];   // 小时

            if ("0".equals(minute) && "*".equals(hour)) {
                // 每小时整点
                return lastRun.getHour() != now.getHour() || lastRun.getDayOfYear() != now.getDayOfYear();
            }
            if ("0".equals(minute)) {
                try {
                    int h = Integer.parseInt(hour);
                    if (h == now.getHour() && lastRun.getDayOfYear() != now.getDayOfYear()) return true;
                } catch (NumberFormatException ignored) {}
            }
        }
        return false;
    }

    private void executeTask(ScheduledTask task) {
        try {
            task.setLastRunAt(LocalDateTime.now());
            task.setLastStatus("running");
            taskMapper.updateById(task);

            switch (task.getTaskType()) {
                case "cleanup" -> {
                    int days = 30;
                    try {
                        if (task.getConfigJson() != null) {
                            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(task.getConfigJson());
                            if (node.has("daysOld")) days = node.get("daysOld").asInt();
                        }
                    } catch (Exception ignored) {}
                    cleanupController.execute(days);
                    log.info("Scheduled cleanup completed: {} days", days);
                }
                default -> log.warn("Unknown task type: {}", task.getTaskType());
            }

            task.setLastStatus("success");
        } catch (Exception e) {
            task.setLastStatus("failed");
            log.error("Scheduled task '{}' failed: {}", task.getName(), e.getMessage());
        }
        taskMapper.updateById(task);
    }
}
