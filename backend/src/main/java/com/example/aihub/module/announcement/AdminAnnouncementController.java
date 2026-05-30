package com.example.aihub.module.announcement;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.Announcement;
import com.example.aihub.infrastructure.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/announcements")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminAnnouncementController {
    private final AnnouncementService announcementService;

    @GetMapping
    public ApiResult<List<Announcement>> list() {
        return ApiResult.ok(announcementService.list(false));
    }

    @PostMapping
    public ApiResult<Announcement> create(@RequestParam String title, @RequestParam String content,
                                           @RequestParam(defaultValue = "normal") String priority) {
        return ApiResult.ok(announcementService.create(title, content, priority, StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/{id}")
    public ApiResult<Announcement> update(@PathVariable Long id, @RequestParam String title,
                                           @RequestParam String content, @RequestParam(defaultValue = "normal") String priority) {
        return ApiResult.ok(announcementService.update(id, title, content, priority));
    }

    @PostMapping("/{id}/toggle")
    public ApiResult<Void> toggle(@PathVariable Long id) {
        announcementService.toggleStatus(id);
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResult.ok();
    }
}
