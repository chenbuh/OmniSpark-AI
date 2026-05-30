package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.mapper.GenerationTaskMapper;
import com.example.aihub.infrastructure.vo.GenerationTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/tasks")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminTasksController {
    private final GenerationTaskMapper taskMapper;

    @GetMapping
    public ApiResult<List<GenerationTaskVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String search) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<GenerationTask>();
        if (status != null && !status.isBlank()) wrapper.eq(GenerationTask::getStatus, status);
        if (taskType != null && !taskType.isBlank()) wrapper.eq(GenerationTask::getTaskType, taskType);
        if (search != null && !search.isBlank()) wrapper.like(GenerationTask::getPrompt, search);
        wrapper.orderByDesc(GenerationTask::getId);
        return ApiResult.ok(taskMapper.selectList(wrapper).stream().map(t -> {
            GenerationTaskVO vo = com.example.aihub.common.util.VoMapper.copy(t, GenerationTaskVO.class);
            return vo;
        }).collect(Collectors.toList()));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        taskMapper.deleteById(id);
        return ApiResult.ok();
    }
}
