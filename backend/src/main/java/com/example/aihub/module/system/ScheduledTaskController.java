package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.infrastructure.entity.ScheduledTask;
import com.example.aihub.infrastructure.service.DynamicSchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/scheduled-tasks")
@SaCheckLogin
@SaCheckRole("admin")
public class ScheduledTaskController {
    private final DynamicSchedulerService schedulerService;

    @GetMapping
    public ApiResult<PageResult<ScheduledTask>> list(@RequestParam(defaultValue = "1") long page,
                                                     @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(schedulerService.page(page, pageSize));
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
