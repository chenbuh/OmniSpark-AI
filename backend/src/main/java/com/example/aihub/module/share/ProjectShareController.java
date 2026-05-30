package com.example.aihub.module.share;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.dto.ShareSaveDTO;
import com.example.aihub.infrastructure.service.ProjectShareService;
import com.example.aihub.infrastructure.vo.ProjectShareVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/project-shares")
@SaCheckLogin
public class ProjectShareController {
    private final ProjectShareService shareService;

    @GetMapping("/{projectId}")
    public ApiResult<List<ProjectShareVO>> list(@PathVariable Long projectId) {
        return ApiResult.ok(shareService.listShares(projectId));
    }

    @PostMapping
    public ApiResult<ProjectShareVO> create(@Valid @RequestBody ShareSaveDTO dto) {
        return ApiResult.ok(shareService.addShare(dto));
    }

    @PutMapping("/{shareId}/permission")
    public ApiResult<ProjectShareVO> updatePermission(@PathVariable Long shareId, @RequestParam String permission) {
        return ApiResult.ok(shareService.updatePermission(shareId, permission));
    }

    @DeleteMapping("/{shareId}")
    public ApiResult<Void> remove(@PathVariable Long shareId) {
        shareService.removeShare(shareId);
        return ApiResult.ok();
    }
}
