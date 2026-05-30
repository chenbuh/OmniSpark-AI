package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.vo.AssetVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

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
    private final com.example.aihub.infrastructure.mapper.ProjectShareMapper projectShareMapper;
    private final com.example.aihub.infrastructure.mapper.TeamMemberMapper teamMemberMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;

    @Value("${server.port:8080}")
    private String serverPort;

    public List<AssetVO> list(Long projectId, String assetType, Long taskId) {
        LambdaQueryWrapper<Asset> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectAccessGuard.assertAccess(projectId);
            wrapper.eq(Asset::getProjectId, projectId);
        } else {
            // 未指定项目时，仅返回当前用户可访问项目下的资产，避免返回全量数据
            List<Long> accessibleIds = projectAccessGuard.accessibleProjectIds();
            if (accessibleIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(Asset::getProjectId, accessibleIds);
        }
        if (assetType != null && !assetType.isBlank()) {
            wrapper.eq(Asset::getAssetType, assetType);
        }
        if (taskId != null) {
            wrapper.eq(Asset::getTaskId, taskId);
        }
        wrapper.orderByDesc(Asset::getId);
        return assetMapper.selectList(wrapper).stream().map(item -> VoMapper.copy(item, AssetVO.class)).toList();
    }

    public List<AssetVO> listShared(Long userId, String assetType) {
        // 始终以当前登录用户为准，忽略请求参数，避免越权查看他人共享资产
        Long currentUserId = com.example.aihub.common.util.SecurityUtil.loginUserId();
        // 查询用户所在的所有团队
        var teamWrapper = new LambdaQueryWrapper<com.example.aihub.infrastructure.entity.TeamMember>();
        teamWrapper.eq(com.example.aihub.infrastructure.entity.TeamMember::getUserId, currentUserId);
        teamWrapper.eq(com.example.aihub.infrastructure.entity.TeamMember::getStatus, 1);
        var memberships = teamMemberMapper.selectList(teamWrapper);
        if (memberships.isEmpty()) return List.of();

        var teamIds = memberships.stream().map(com.example.aihub.infrastructure.entity.TeamMember::getTeamId).toList();

        // 查询这些团队共享的项目
        var shareWrapper = new LambdaQueryWrapper<com.example.aihub.infrastructure.entity.ProjectShare>();
        shareWrapper.in(com.example.aihub.infrastructure.entity.ProjectShare::getTeamId, teamIds);
        var shares = projectShareMapper.selectList(shareWrapper);
        if (shares.isEmpty()) return List.of();

        var projectIds = shares.stream().map(com.example.aihub.infrastructure.entity.ProjectShare::getProjectId).toList();

        // 查询这些项目的资产
        var assetWrapper = new LambdaQueryWrapper<Asset>();
        assetWrapper.in(Asset::getProjectId, projectIds);
        if (assetType != null && !assetType.isBlank()) {
            assetWrapper.eq(Asset::getAssetType, assetType);
        }
        assetWrapper.orderByDesc(Asset::getId);
        return assetMapper.selectList(assetWrapper).stream().map(item -> VoMapper.copy(item, AssetVO.class)).toList();
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
            Path uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
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
            return VoMapper.copy(asset, AssetVO.class);
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
        assetMapper.deleteById(id);
        deleteAssetFile(asset.getFileUrl());
    }

    /**
     * 删除资产对应的物理文件,仅允许删除 uploads 目录内的文件,避免误删/越权删除。
     * 文件删除失败只记日志、不影响 DB 删除事务(孤儿文件可由文件清理任务兜底)。
     */
    public void deleteAssetFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }
        try {
            Path base = Paths.get("uploads").toAbsolutePath().normalize();
            // 仅处理本地 /uploads/ 资源,外链(http)不动
            String relative = fileUrl.startsWith("/uploads/")
                    ? fileUrl.substring("/uploads/".length())
                    : (fileUrl.startsWith("uploads/") ? fileUrl.substring("uploads/".length()) : null);
            if (relative == null) {
                return;
            }
            Path target = base.resolve(relative).normalize();
            if (!target.startsWith(base)) {
                log.warn("跳过删除越界文件: {}", fileUrl);
                return;
            }
            Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("删除资产物理文件失败: {} - {}", fileUrl, ex.getMessage());
        }
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
        return VoMapper.copy(asset, AssetVO.class);
    }

    /** 管理员删除资产:删 DB 记录并联动删物理文件,不做项目归属校验(由 admin 角色保证)。 */
    @Transactional(rollbackFor = Exception.class)
    public void adminDelete(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            return;
        }
        assetMapper.deleteById(id);
        deleteAssetFile(asset.getFileUrl());
    }

    /** 删除某任务关联的全部资产(DB 记录 + 物理文件),用于任务删除时清理产物。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByTaskId(Long taskId) {
        List<Asset> assets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>().eq(Asset::getTaskId, taskId));
        for (Asset asset : assets) {
            assetMapper.deleteById(asset.getId());
            deleteAssetFile(asset.getFileUrl());
        }
    }
}
