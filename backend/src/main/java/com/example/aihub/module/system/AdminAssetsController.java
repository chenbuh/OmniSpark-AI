package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.mapper.AssetMapper;
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

    @GetMapping
    public ApiResult<List<AssetVO>> list(@RequestParam(required = false) String assetType,
                                          @RequestParam(required = false) String search) {
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.aihub.infrastructure.entity.Asset>();
        if (assetType != null && !assetType.isBlank()) wrapper.eq(com.example.aihub.infrastructure.entity.Asset::getAssetType, assetType);
        if (search != null && !search.isBlank()) wrapper.like(com.example.aihub.infrastructure.entity.Asset::getFileName, search);
        wrapper.orderByDesc(com.example.aihub.infrastructure.entity.Asset::getId);
        return ApiResult.ok(assetMapper.selectList(wrapper).stream()
                .map(a -> com.example.aihub.common.util.VoMapper.copy(a, AssetVO.class))
                .toList());
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        assetMapper.deleteById(id);
        return ApiResult.ok();
    }
}
