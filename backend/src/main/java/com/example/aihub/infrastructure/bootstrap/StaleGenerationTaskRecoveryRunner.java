package com.example.aihub.infrastructure.bootstrap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.mapper.GenerationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class StaleGenerationTaskRecoveryRunner implements CommandLineRunner {
    private final GenerationTaskMapper taskMapper;

    @Override
    public void run(String... args) {
        List<GenerationTask> staleTasks = taskMapper.selectList(new LambdaQueryWrapper<GenerationTask>()
                .in(GenerationTask::getStatus, List.of("running", "pending")));
        if (staleTasks.isEmpty()) {
            return;
        }
        for (GenerationTask task : staleTasks) {
            task.setStatus("failed");
            task.setProgress(0);
            task.setProgressText("生成失败");
            task.setErrorMessage("服务已重启，上一轮生成任务已中断，请重新提交");
            taskMapper.updateById(task);
        }
        log.info("Recovered {} stale generation task(s) left in running/pending state after restart.", staleTasks.size());
    }
}
