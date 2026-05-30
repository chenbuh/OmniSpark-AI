package com.example.aihub.module.notification;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.service.NotificationService;
import com.example.aihub.infrastructure.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
@SaCheckLogin
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/unread")
    public ApiResult<List<NotificationVO>> unread() {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResult.ok(notificationService.listUnread(userId));
    }

    @GetMapping
    public ApiResult<List<NotificationVO>> list(@RequestParam(defaultValue = "50") int limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResult.ok(notificationService.listAll(userId, limit));
    }

    @GetMapping("/count")
    public ApiResult<Long> count() {
        Long userId = StpUtil.getLoginIdAsLong();
        return ApiResult.ok(notificationService.countUnread(userId));
    }

    @PostMapping("/{id}/read")
    public ApiResult<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ApiResult.ok();
    }

    @PostMapping("/read-all")
    public ApiResult<Void> markAllRead() {
        Long userId = StpUtil.getLoginIdAsLong();
        notificationService.markAllRead(userId);
        return ApiResult.ok();
    }
}
