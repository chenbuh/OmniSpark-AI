package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.service.AssetService;
import com.example.aihub.infrastructure.vo.AssetVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
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
    public ApiResult<PageResult<AssetVO>> list(@RequestParam(required = false) String assetType,
                                          @RequestParam(required = false) String search,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "12") long pageSize) {
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 12);
        var wrapper = new LambdaQueryWrapper<Asset>();
        if (assetType != null && !assetType.isBlank()) wrapper.eq(Asset::getAssetType, assetType);
        if (search != null && !search.isBlank()) wrapper.like(Asset::getFileName, search);
        wrapper.orderByDesc(Asset::getId);

        var p = assetMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<AssetVO> records = p.getRecords().stream()
                .map(assetService::toVO)
                .toList();
        return ApiResult.ok(new PageResult<>(p.getTotal(), p.getPages(), records));
    }

    @GetMapping("/{id}")
    public ApiResult<AssetVO> detail(@PathVariable Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            return ApiResult.fail("资产不存在");
        }
        return ApiResult.ok(assetService.toVO(asset));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        AssetService.AssetDownloadPayload payload = assetService.resolveAdminDownload(id);
        return buildDownloadResponse(payload);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        assetService.adminDelete(id);
        return ApiResult.ok();
    }

    private ResponseEntity<Resource> buildDownloadResponse(AssetService.AssetDownloadPayload payload) {
        FileSystemResource resource = new FileSystemResource(payload.filePath());
        try {
            MediaType mediaType = MediaType.parseMediaType(payload.mimeType());
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(resource.contentLength())
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(payload.fileName(), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (Exception ex) {
            throw new BusinessException("资产下载失败，请稍后重试");
        }
    }
}
