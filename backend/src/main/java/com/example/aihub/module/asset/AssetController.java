package com.example.aihub.module.asset;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.service.AssetService;
import com.example.aihub.infrastructure.vo.AssetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assets")
@SaCheckLogin
public class AssetController {
    private final AssetService assetService;

    @GetMapping
    public ApiResult<List<AssetVO>> list(@RequestParam(name = "projectId", required = false) Long projectId,
                                         @RequestParam(name = "assetType", required = false) String assetType,
                                         @RequestParam(name = "taskId", required = false) Long taskId) {
        return ApiResult.ok(assetService.list(projectId, assetType, taskId));
    }

    @GetMapping("/shared")
    public ApiResult<List<AssetVO>> listShared(@RequestParam Long userId,
                                               @RequestParam(required = false) String assetType) {
        return ApiResult.ok(assetService.listShared(userId, assetType));
    }

    @PostMapping("/upload")
    public ApiResult<AssetVO> upload(@RequestParam Long projectId, @RequestParam("file") MultipartFile file) {
        return ApiResult.ok(assetService.upload(projectId, file));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/favorite")
    public ApiResult<AssetVO> favorite(@PathVariable Long id) {
        return ApiResult.ok(assetService.favorite(id));
    }
}
