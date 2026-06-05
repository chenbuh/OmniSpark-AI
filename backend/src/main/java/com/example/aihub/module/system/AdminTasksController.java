package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.mapper.GenerationTaskMapper;
import com.example.aihub.infrastructure.service.GenerationService;
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
    private final GenerationService generationService;

    @GetMapping
    public ApiResult<PageResult<GenerationTaskVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 10);
        var wrapper = new LambdaQueryWrapper<GenerationTask>();
        if (status != null && !status.isBlank()) wrapper.eq(GenerationTask::getStatus, status);
        if (taskType != null && !taskType.isBlank()) wrapper.eq(GenerationTask::getTaskType, taskType);
        if (search != null && !search.isBlank()) wrapper.like(GenerationTask::getPrompt, search);
        wrapper.orderByDesc(GenerationTask::getId);

        var p = taskMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<GenerationTaskVO> records = p.getRecords().stream()
                .map(t -> com.example.aihub.common.util.VoMapper.copy(t, GenerationTaskVO.class))
                .collect(Collectors.toList());
        return ApiResult.ok(new PageResult<>(p.getTotal(), p.getPages(), records));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        generationService.adminDelete(id);
        return ApiResult.ok();
    }
}
