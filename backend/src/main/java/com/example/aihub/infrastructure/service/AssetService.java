package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.security.UploadAccessSignatureService;
import com.example.aihub.common.storage.UploadStorageResolver;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.ProjectShare;
import com.example.aihub.infrastructure.entity.StyleCard;
import com.example.aihub.infrastructure.entity.TeamMember;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.mapper.ProjectShareMapper;
import com.example.aihub.infrastructure.mapper.StyleCardMapper;
import com.example.aihub.infrastructure.mapper.TeamMemberMapper;
import com.example.aihub.infrastructure.vo.AssetVO;
import com.example.aihub.infrastructure.vo.AssetStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssetService {
    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024; // 50MB
    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
            "mp4", "webm", "mov", "m4v"
    );

    private final AssetMapper assetMapper;
    private final ProjectShareMapper projectShareMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final StyleCardMapper styleCardMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;
    private final UploadAccessSignatureService uploadAccessSignatureService;
    private final UploadStorageResolver uploadStorageResolver;
    private final SubtitleService subtitleService;

    public List<AssetVO> list(Long projectId, String assetType, Long taskId, int limit) {
        int safeLimit = PagingUtil.clampLimit(limit, 100, 100);
        List<Long> projectIds = resolveOwnProjectIds(projectId);
        if (projectIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Asset::getProjectId, projectIds);
        if (assetType != null && !assetType.isBlank()) {
            wrapper.eq(Asset::getAssetType, assetType);
        }
        if (taskId != null) {
            wrapper.eq(Asset::getTaskId, taskId);
        }
        wrapper.orderByDesc(Asset::getId);
        wrapper.last("LIMIT " + safeLimit);
        return assetMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public List<AssetVO> listShared(Long userId, String assetType, int limit) {
        int safeLimit = PagingUtil.clampLimit(limit, 100, 100);
        // 始终以当前登录用户为准，忽略请求参数，避免越权查看他人共享资产
        List<Long> projectIds = resolveSharedProjectIds();
        if (projectIds.isEmpty()) {
            return List.of();
        }
        var assetWrapper = new LambdaQueryWrapper<Asset>();
        assetWrapper.in(Asset::getProjectId, projectIds);
        if (assetType != null && !assetType.isBlank()) {
            assetWrapper.eq(Asset::getAssetType, assetType);
        }
        assetWrapper.orderByDesc(Asset::getId);
        assetWrapper.last("LIMIT " + safeLimit);
        return assetMapper.selectList(assetWrapper).stream().map(this::toVO).toList();
    }

    public PageResult<AssetVO> page(String scope, Long projectId, String assetType, Long taskId, Boolean favorite,
                                    String search, String sort, long page, long pageSize) {
        List<Long> projectIds = resolveProjectIds(scope, projectId);
        if (projectIds.isEmpty()) {
            return new PageResult<>(0, 0, List.of());
        }
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 24);
        LambdaQueryWrapper<Asset> wrapper = buildAssetFilter(projectIds, assetType, taskId, favorite, search);
        applySort(wrapper, sort);

        Page<Asset> result = assetMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        List<AssetVO> records = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(result.getTotal(), result.getPages(), records);
    }

    public AssetStatsVO stats(String scope, Long projectId) {
        List<Long> projectIds = resolveProjectIds(scope, projectId);
        if (projectIds.isEmpty()) {
            return new AssetStatsVO(0, 0, 0, 0, 0);
        }
        long total = countAssets(projectIds, null, null);
        long imageCount = countAssets(projectIds, "image", null);
        long videoCount = countAssets(projectIds, "video", null);
        long referenceCount = countAssets(projectIds, "reference", null);
        long favoriteCount = countAssets(projectIds, null, true);
        return new AssetStatsVO(total, imageCount, videoCount, referenceCount, favoriteCount);
    }

    public AssetVO get(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        projectAccessGuard.assertAccess(asset.getProjectId());
        return toVO(asset);
    }

    public List<AssetVO> listVersions(Long id, int limit) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        projectAccessGuard.assertAccess(asset.getProjectId());
        if (asset.getPrompt() == null || asset.getPrompt().isBlank()) {
            return List.of();
        }

        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .eq(Asset::getProjectId, asset.getProjectId())
                .eq(Asset::getPrompt, asset.getPrompt())
                .orderByDesc(Asset::getId);
        if (asset.getModelName() == null || asset.getModelName().isBlank()) {
            wrapper.and(q -> q.isNull(Asset::getModelName).or().eq(Asset::getModelName, ""));
        } else {
            wrapper.eq(Asset::getModelName, asset.getModelName());
        }
        wrapper.last("LIMIT " + PagingUtil.clampLimit(limit, 12, 20));
        return assetMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AssetVO upload(Long projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        projectAccessGuard.assertAccess(projectId);

        if (file.getSize() > MAX_UPLOAD_SIZE) {
            throw new BusinessException("文件过大，最大允许 " + (MAX_UPLOAD_SIZE / 1024 / 1024) + "MB");
        }
        String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        // 仅保留文件名本身，剥离任何路径分隔符，杜绝 ../ 逃逸
        String baseName = Paths.get(original).getFileName().toString();
        String ext = extractExtension(baseName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException("不支持的文件类型，仅允许图片或视频");
        }
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new BusinessException("不支持的文件内容类型，仅允许图片或视频");
        }

        try {
            Path uploadDir = uploadStorageResolver.getUploadRoot();
            Files.createDirectories(uploadDir);
            String safeName = UUID.randomUUID() + "_" + baseName;
            Path target = uploadDir.resolve(safeName).normalize();
            // 二次确认最终落盘路径仍在 uploads 目录内
            if (!target.startsWith(uploadDir)) {
                throw new BusinessException("非法的文件名");
            }
            Files.copy(file.getInputStream(), target);

            Asset asset = new Asset();
            asset.setProjectId(projectId);
            asset.setAssetType("reference");
            asset.setFileName(baseName);
            asset.setFileUrl("/uploads/" + safeName);
            asset.setThumbUrl("/uploads/" + safeName);
            asset.setMimeType(contentType);
            asset.setFileSize(file.getSize());
            asset.setFavorite(0);
            assetMapper.insert(asset);
            return toVO(asset);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("上传失败，请稍后重试");
        }
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        projectAccessGuard.assertAccess(asset.getProjectId());
        detachAssetReferences(List.of(asset.getId()));
        subtitleService.deleteByAssetIds(List.of(asset.getId()));
        assetMapper.deleteById(id);
        deleteAssetFile(asset.getFileUrl());
        deleteAssetFile(asset.getThumbUrl());
    }

    /**
     * 删除资产对应的物理文件,仅允许删除 uploads 目录内的文件,避免误删/越权删除。
     * 文件删除失败只记日志、不影响 DB 删除事务(孤儿文件可由文件清理任务兜底)。
     */
    public void deleteAssetFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        List<Path> targets = uploadStorageResolver.resolveLocalUploadPaths(fileUrl);
        if (targets.isEmpty()) {
            return;
        }
        for (Path target : targets) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception ex) {
                log.warn("删除资产物理文件失败: {} - {}", target, ex.getMessage());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return;
        }
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>().eq(Asset::getProjectId, projectId));
        if (assets.isEmpty()) {
            return;
        }
        Set<String> localFiles = new LinkedHashSet<>();
        List<Long> assetIds = assets.stream().map(Asset::getId).toList();
        for (Asset asset : assets) {
            if (asset.getFileUrl() != null && !asset.getFileUrl().isBlank()) {
                localFiles.add(asset.getFileUrl());
            }
            if (asset.getThumbUrl() != null && !asset.getThumbUrl().isBlank()) {
                localFiles.add(asset.getThumbUrl());
            }
        }
        detachAssetReferences(assetIds);
        subtitleService.deleteByAssetIds(assetIds);
        assetMapper.delete(new LambdaQueryWrapper<Asset>().eq(Asset::getProjectId, projectId));
        localFiles.forEach(this::deleteAssetFile);
    }

    @Transactional(rollbackFor = Exception.class)
    public AssetVO favorite(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        projectAccessGuard.assertAccess(asset.getProjectId());
        asset.setFavorite(asset.getFavorite() != null && asset.getFavorite() == 1 ? 0 : 1);
        assetMapper.updateById(asset);
        return toVO(asset);
    }

    public AssetVO toVO(Asset asset) {
        AssetVO vo = VoMapper.copy(asset, AssetVO.class);
        vo.setFileUrl(uploadAccessSignatureService.signProjectUrl(vo.getFileUrl(), asset.getProjectId()));
        vo.setThumbUrl(uploadAccessSignatureService.signProjectUrl(vo.getThumbUrl(), asset.getProjectId()));
        return vo;
    }

    /** 管理员删除资产:删 DB 记录并联动删物理文件,不做项目归属校验(由 admin 角色保证)。 */
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        detachAssetReferences(List.of(asset.getId()));
        subtitleService.deleteByAssetIds(List.of(asset.getId()));
        assetMapper.deleteById(id);
        deleteAssetFile(asset.getFileUrl());
        deleteAssetFile(asset.getThumbUrl());
    }

    /** 删除某任务关联的全部资产(DB 记录 + 物理文件),用于任务删除时清理产物。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByTaskId(Long taskId) {
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>().eq(Asset::getTaskId, taskId));
        if (assets.isEmpty()) {
            return;
        }
        List<Long> assetIds = assets.stream().map(Asset::getId).toList();
        detachAssetReferences(assetIds);
        subtitleService.deleteByAssetIds(assetIds);
        for (Asset asset : assets) {
            assetMapper.deleteById(asset.getId());
            deleteAssetFile(asset.getFileUrl());
            deleteAssetFile(asset.getThumbUrl());
        }
    }

    private void detachAssetReferences(List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        styleCardMapper.update(null, new LambdaUpdateWrapper<StyleCard>()
                .in(StyleCard::getRefAssetId, assetIds)
                .set(StyleCard::getRefAssetId, null));
    }

    private List<Long> resolveProjectIds(String scope, Long projectId) {
        return "shared".equalsIgnoreCase(scope)
                ? resolveSharedProjectIds()
                : resolveOwnProjectIds(projectId);
    }

    private List<Long> resolveOwnProjectIds(Long projectId) {
        if (projectId != null) {
            projectAccessGuard.assertAccess(projectId);
            return List.of(projectId);
        }
        return projectAccessGuard.accessibleProjectIds();
    }

    private List<Long> resolveSharedProjectIds() {
        Long currentUserId = SecurityUtil.loginUserId();
        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, currentUserId)
                        .eq(TeamMember::getStatus, 1));
        if (memberships.isEmpty()) {
            return List.of();
        }
        Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
        List<ProjectShare> shares = projectShareMapper.selectList(
                new LambdaQueryWrapper<ProjectShare>().in(ProjectShare::getTeamId, teamIds));
        if (shares.isEmpty()) {
            return List.of();
        }
        return shares.stream().map(ProjectShare::getProjectId).distinct().toList();
    }

    private LambdaQueryWrapper<Asset> buildAssetFilter(List<Long> projectIds, String assetType, Long taskId, Boolean favorite, String search) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .in(Asset::getProjectId, projectIds);
        if (assetType != null && !assetType.isBlank()) {
            wrapper.eq(Asset::getAssetType, assetType);
        }
        if (taskId != null) {
            wrapper.eq(Asset::getTaskId, taskId);
        }
        if (favorite != null) {
            wrapper.eq(Asset::getFavorite, favorite ? 1 : 0);
        }
        if (search != null && !search.isBlank()) {
            String keyword = search.trim();
            wrapper.and(q -> q.like(Asset::getFileName, keyword)
                    .or().like(Asset::getPrompt, keyword)
                    .or().like(Asset::getModelName, keyword));
        }
        return wrapper;
    }

    private long countAssets(List<Long> projectIds, String assetType, Boolean favorite) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<Asset>()
                .in(Asset::getProjectId, projectIds);
        if (assetType != null && !assetType.isBlank()) {
            wrapper.eq(Asset::getAssetType, assetType);
        }
        if (favorite != null) {
            wrapper.eq(Asset::getFavorite, favorite ? 1 : 0);
        }
        Long count = assetMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    private void applySort(LambdaQueryWrapper<Asset> wrapper, String sort) {
        String normalized = sort == null ? "latest" : sort.trim().toLowerCase(java.util.Locale.ROOT);
        switch (normalized) {
            case "oldest":
                wrapper.orderByAsc(Asset::getId);
                break;
            case "size":
                wrapper.orderByDesc(Asset::getFileSize).orderByDesc(Asset::getId);
                break;
            case "name":
                wrapper.orderByAsc(Asset::getFileName).orderByDesc(Asset::getId);
                break;
            case "favorite":
                wrapper.orderByDesc(Asset::getFavorite).orderByDesc(Asset::getId);
                break;
            case "latest":
            default:
                wrapper.orderByDesc(Asset::getId);
                break;
        }
    }
}
