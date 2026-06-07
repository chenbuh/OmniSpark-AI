package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.PagingUtil;
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
        return ApiResult.ok(schedulerService.page(page, PagingUtil.clampPageSize(pageSize, 100)));
    }

    @PostMapping("/{id}/toggle")
    @RateLimit(count = 20, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "操作过于频繁")
    public ApiResult<ScheduledTask> toggle(@PathVariable Long id) {
        return ApiResult.ok(schedulerService.toggle(id));
    }

    @PostMapping("/{id}/run")
    @RateLimit(count = 10, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "操作过于频繁")
    public ApiResult<Void> runNow(@PathVariable Long id) {
        schedulerService.runNow(id);
        return ApiResult.ok();
    }

    @PostMapping
    @RateLimit(count = 20, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "创建过于频繁")
    public ApiResult<ScheduledTask> create(@RequestBody ScheduledTask task) {
        return ApiResult.ok(schedulerService.create(task));
    }

    @PutMapping("/{id}")
    @RateLimit(count = 20, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "操作过于频繁")
    public ApiResult<ScheduledTask> update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        task.setId(id);
        return ApiResult.ok(schedulerService.update(task));
    }

    @DeleteMapping("/{id}")
    @RateLimit(count = 20, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "删除过于频繁")
    public ApiResult<Void> delete(@PathVariable Long id) {
        schedulerService.delete(id);
        return ApiResult.ok();
    }
}
