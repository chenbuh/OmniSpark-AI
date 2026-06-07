package com.example.aihub.module.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.storage.UploadStorageResolver;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.Subtitle;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.mapper.SubtitleMapper;
import com.example.aihub.infrastructure.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/files")
@SaCheckLogin
@SaCheckRole("admin")
public class FileManagerController {
    private static final long STATS_CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(10);

    private final UploadStorageResolver uploadStorageResolver;
    private final AssetMapper assetMapper;
    private final SubtitleMapper subtitleMapper;
    private final AssetService assetService;
    private volatile Map<String, Object> cachedStats;
    private volatile long cachedStatsAt;

    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(defaultValue = "") String path) {
        String requestedPath = normalizeRequestedPath(path);
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        List<Map<String, Object>> dirs = new ArrayList<>();
        result.put("available", false);
        result.put("message", "");
        result.put("requestedPath", requestedPath);
        result.put("currentPath", requestedPath);
        result.put("parentPath", getParentPath(requestedPath));
        result.put("uploadDir", "");
        result.put("items", List.of());
        result.put("total", 0);

        try {
            Path baseDir = uploadStorageResolver.getUploadRoot();
            result.put("uploadDir", baseDir.toString());
            if (!Files.exists(baseDir)) {
                result.put("message", "上传目录尚未初始化，当前没有可浏览文件");
                return ApiResult.ok(result);
            }
            if (!Files.isDirectory(baseDir)) {
                result.put("message", "上传目录配置无效，目标不是目录");
                return ApiResult.ok(result);
            }

            Path targetDir = baseDir.resolve(requestedPath).normalize();

            // 安全检查：禁止超出 uploads 目录
            if (!targetDir.startsWith(baseDir)) {
                result.put("message", "路径不合法");
                return ApiResult.ok(result);
            }

            if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
                result.put("message", "目录不存在");
                return ApiResult.ok(result);
            }
            if (!isRealPathWithinBase(targetDir, baseDir)) {
                result.put("message", "目录超出允许访问范围");
                return ApiResult.ok(result);
            }

            try (var stream = Files.list(targetDir)) {
                stream.forEach(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", p.getFileName().toString());
                    item.put("size", p.toFile().length());
                    item.put("lastModified", p.toFile().lastModified());
                    item.put("isDir", Files.isDirectory(p));

                    try {
                        if (!Files.isDirectory(p)) {
                            String mime = Files.probeContentType(p);
                            item.put("mimeType", mime != null ? mime : "application/octet-stream");
                        }
                    } catch (Exception ignored) {
                        item.put("mimeType", "application/octet-stream");
                    }

                    // 相对路径
                    String relPath = baseDir.relativize(p).toString().replace("\\", "/");
                    item.put("relativePath", relPath);

                    if (Files.isDirectory(p)) {
                        dirs.add(item);
                    } else {
                        files.add(item);
                    }
                });
            }

            // 目录在前，文件在后，按名称排序
            dirs.sort((a, b) -> ((String) a.get("name")).compareToIgnoreCase((String) b.get("name")));
            files.sort((a, b) -> ((String) a.get("name")).compareToIgnoreCase((String) b.get("name")));

            List<Map<String, Object>> all = new ArrayList<>();
            all.addAll(dirs);
            all.addAll(files);

            result.put("items", all);
            result.put("available", true);
            result.put("currentPath", requestedPath);
            result.put("parentPath", getParentPath(requestedPath));
            result.put("total", all.size());

        } catch (Exception e) {
            result.put("message", "读取文件列表失败: " + e.getMessage());
        }

        return ApiResult.ok(result);
    }

    @DeleteMapping
    @RateLimit(count = 20, seconds = 60, dimension = RateLimit.Dimension.USER_API, message = "删除过于频繁")
    public ApiResult<Void> delete(@RequestParam String path) {
        try {
            Path baseDir = uploadStorageResolver.getUploadRoot();
            Path target = baseDir.resolve(path).normalize();
            if (!target.startsWith(baseDir)) {
                return ApiResult.fail("路径不合法");
            }
            if (!Files.exists(target)) {
                return ApiResult.fail("文件或目录不存在");
            }
            if (!isRealPathWithinBase(target, baseDir)) {
                return ApiResult.fail("目标超出允许访问范围");
            }
            clearDatabaseReferences(target, baseDir);
            deleteRecursively(target);
            cachedStats = null;
            cachedStatsAt = 0;
            return ApiResult.ok();
        } catch (Exception e) {
            return ApiResult.fail("删除失败: " + e.getMessage());
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<Resource> preview(@RequestParam String path) {
        try {
            Path baseDir = uploadStorageResolver.getUploadRoot();
            Path target = baseDir.resolve(path).normalize();
            if (!target.startsWith(baseDir) || !Files.exists(target) || !isRealPathWithinBase(target, baseDir)) {
                return ResponseEntity.notFound().build();
            }
            String mime = Files.probeContentType(target);
            if (mime == null) mime = "application/octet-stream";
            Resource resource = new FileSystemResource(target);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    public synchronized ApiResult<Map<String, Object>> stats() {
        long now = System.currentTimeMillis();
        if (cachedStats != null && now - cachedStatsAt < STATS_CACHE_TTL_MS) {
            return ApiResult.ok(cachedStats);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("available", false);
        stats.put("message", "");
        stats.put("uploadDir", "");
        stats.put("uploadDirExists", false);
        stats.put("totalSize", 0L);
        stats.put("fileCount", 0L);
        stats.put("totalSizeReadable", formatSize(0L));
        try {
            Path baseDir = uploadStorageResolver.getUploadRoot();
            stats.put("uploadDir", baseDir.toString());
            if (!Files.exists(baseDir)) {
                stats.put("message", "上传目录尚未初始化，当前没有可统计文件");
                cachedStats = stats;
                cachedStatsAt = now;
                return ApiResult.ok(stats);
            }
            if (!Files.isDirectory(baseDir)) {
                stats.put("message", "上传目录配置无效，目标不是目录");
                cachedStats = stats;
                cachedStatsAt = now;
                return ApiResult.ok(stats);
            }

            long totalSize = 0L;
            long fileCount = 0L;
            stats.put("uploadDirExists", true);
            try (var stream = Files.walk(baseDir)) {
                var iterator = stream.iterator();
                while (iterator.hasNext()) {
                    Path current = iterator.next();
                    if (Files.isRegularFile(current)) {
                        totalSize += Files.size(current);
                        fileCount++;
                    }
                }
            }
            stats.put("available", true);
            stats.put("totalSize", totalSize);
            stats.put("fileCount", fileCount);
            stats.put("totalSizeReadable", formatSize(totalSize));
        } catch (Exception e) {
            stats.put("message", "读取文件统计失败: " + e.getMessage());
        }
        cachedStats = stats;
        cachedStatsAt = now;
        return ApiResult.ok(stats);
    }

    private String normalizeRequestedPath(String path) {
        return path == null ? "" : path.trim();
    }

    private String getParentPath(String path) {
        if (path == null || path.isEmpty() || path.isBlank()) return "";
        int idx = path.lastIndexOf('/');
        if (idx <= 0) return "";
        return path.substring(0, idx);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void clearDatabaseReferences(Path target, Path baseDir) {
        String uploadUrl = toUploadUrl(target, baseDir);
        if (uploadUrl.isBlank()) {
            return;
        }

        List<Asset> linkedAssets = assetMapper.selectList(new LambdaQueryWrapper<Asset>()
                .and(wrapper -> wrapper.eq(Asset::getFileUrl, uploadUrl)
                        .or()
                        .likeRight(Asset::getFileUrl, uploadUrl + "/")
                        .or()
                        .eq(Asset::getThumbUrl, uploadUrl)
                        .or()
                        .likeRight(Asset::getThumbUrl, uploadUrl + "/")));
        for (Asset asset : linkedAssets) {
            if (asset.getId() != null) {
                assetService.adminDelete(asset.getId());
            }
        }

        subtitleMapper.update(null, new LambdaUpdateWrapper<Subtitle>()
                .and(wrapper -> wrapper.eq(Subtitle::getVoiceUrl, uploadUrl)
                        .or()
                        .likeRight(Subtitle::getVoiceUrl, uploadUrl + "/"))
                .set(Subtitle::getVoiceUrl, null));
    }

    private void deleteRecursively(Path target) throws Exception {
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            try (var stream = Files.walk(target)) {
                stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (RuntimeException ex) {
                if (ex.getCause() instanceof Exception cause) {
                    throw cause;
                }
                throw ex;
            }
            return;
        }
        Files.deleteIfExists(target);
    }

    private String toUploadUrl(Path target, Path baseDir) {
        Path relativePath = baseDir.relativize(target);
        String normalized = relativePath.toString().replace("\\", "/");
        if (normalized.isBlank()) {
            return "";
        }
        return "/uploads/" + normalized;
    }

    private boolean isRealPathWithinBase(Path target, Path baseDir) {
        try {
            Path realBaseDir = baseDir.toRealPath();
            Path realTarget = target.toRealPath();
            return realTarget.startsWith(realBaseDir);
        } catch (Exception ex) {
            return false;
        }
    }
}
