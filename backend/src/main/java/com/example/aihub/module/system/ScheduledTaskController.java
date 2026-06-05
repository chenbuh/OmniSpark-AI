package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.ScheduledTask;
import com.example.aihub.infrastructure.service.DynamicSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/scheduled-tasks")
@SaCheckLogin
@SaCheckRole("admin")
public class ScheduledTaskController {
    private final DynamicSchedulerService schedulerService;

    @GetMapping
    public ApiResult<List<ScheduledTask>> list(@RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(schedulerService.list(PagingUtil.clampLimit(limit, 100, 100)));
    }

    @PostMapping("/{id}/toggle")
    public ApiResult<ScheduledTask> toggle(@PathVariable Long id) {
        return ApiResult.ok(schedulerService.toggle(id));
    }

    @PostMapping("/{id}/run")
    public ApiResult<Void> runNow(@PathVariable Long id) {
        schedulerService.runNow(id);
        return ApiResult.ok();
    }

    @PostMapping
    public ApiResult<ScheduledTask> create(@RequestBody ScheduledTask task) {
        return ApiResult.ok(schedulerService.create(task));
    }

    @PutMapping("/{id}")
    public ApiResult<ScheduledTask> update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        task.setId(id);
        return ApiResult.ok(schedulerService.update(task));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        schedulerService.delete(id);
        return ApiResult.ok();
    }
}
