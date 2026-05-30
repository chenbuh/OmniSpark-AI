package com.example.aihub.module.announcement;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.Announcement;
import com.example.aihub.infrastructure.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/announcements")
@SaCheckLogin
public class AnnouncementController {
    private final AnnouncementService announcementService;

    @GetMapping("/active")
    public ApiResult<List<Announcement>> active() {
        return ApiResult.ok(announcementService.list(true));
    }
}
