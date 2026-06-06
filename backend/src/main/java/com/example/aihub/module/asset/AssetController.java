package com.example.aihub.module.asset;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.service.AssetService;
import com.example.aihub.infrastructure.vo.AssetVO;
import com.example.aihub.infrastructure.vo.AssetStatsVO;
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
                                         @RequestParam(name = "taskId", required = false) Long taskId,
                                         @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(assetService.list(projectId, assetType, taskId,
                PagingUtil.clampLimit(limit, 100, 100)));
    }

    @GetMapping("/page")
    public ApiResult<PageResult<AssetVO>> page(@RequestParam(defaultValue = "own") String scope,
                                               @RequestParam(name = "projectId", required = false) Long projectId,
                                               @RequestParam(name = "assetType", required = false) String assetType,
                                               @RequestParam(name = "taskId", required = false) Long taskId,
                                               @RequestParam(name = "favorite", required = false) Boolean favorite,
                                               @RequestParam(name = "search", required = false) String search,
                                               @RequestParam(defaultValue = "latest") String sort,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "24") long pageSize) {
        return ApiResult.ok(assetService.page(scope, projectId, assetType, taskId, favorite, search, sort, page, pageSize));
    }

    @GetMapping("/stats")
    public ApiResult<AssetStatsVO> stats(@RequestParam(defaultValue = "own") String scope,
                                         @RequestParam(name = "projectId", required = false) Long projectId) {
        return ApiResult.ok(assetService.stats(scope, projectId));
    }

    @GetMapping("/{id}")
    public ApiResult<AssetVO> get(@PathVariable Long id) {
        return ApiResult.ok(assetService.get(id));
    }

    @GetMapping("/{id}/versions")
    public ApiResult<List<AssetVO>> versions(@PathVariable Long id,
                                             @RequestParam(defaultValue = "12") int limit) {
        return ApiResult.ok(assetService.listVersions(id, PagingUtil.clampLimit(limit, 12, 20)));
    }

    @GetMapping("/shared")
    public ApiResult<List<AssetVO>> listShared(@RequestParam Long userId,
                                               @RequestParam(required = false) String assetType,
                                               @RequestParam(defaultValue = "100") int limit) {
        return ApiResult.ok(assetService.listShared(userId, assetType,
                PagingUtil.clampLimit(limit, 100, 100)));
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
