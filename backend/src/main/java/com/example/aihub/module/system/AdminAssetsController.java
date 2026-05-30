package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.service.AssetService;
import com.example.aihub.infrastructure.vo.AssetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/assets")
@SaCheckLogin
@SaCheckRole("admin")
public class AdminAssetsController {
    private final AssetMapper assetMapper;
    private final AssetService assetService;

    @GetMapping
    public ApiResult<com.example.aihub.common.result.PageResult<AssetVO>> list(@RequestParam(required = false) String assetType,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "12") long pageSize) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.aihub.infrastructure.entity.Asset>();
        if (assetType != null && !assetType.isBlank()) wrapper.eq(com.example.aihub.infrastructure.entity.Asset::getAssetType, assetType);
        if (search != null && !search.isBlank()) wrapper.like(com.example.aihub.infrastructure.entity.Asset::getFileName, search);
        wrapper.orderByDesc(com.example.aihub.infrastructure.entity.Asset::getId);

        var p = assetMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, pageSize), wrapper);
        List<AssetVO> records = p.getRecords().stream()
                .map(a -> com.example.aihub.common.util.VoMapper.copy(a, AssetVO.class))
                .toList();
        return ApiResult.ok(new com.example.aihub.common.result.PageResult<>(p.getTotal(), p.getPages(), records));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        assetService.adminDelete(id);
        return ApiResult.ok();
    }
}
