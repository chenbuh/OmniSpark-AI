package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.storage.UploadStorageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
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
    private volatile Map<String, Object> cachedStats;
    private volatile long cachedStatsAt;

    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(defaultValue = "") String path) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> files = new ArrayList<>();
        List<Map<String, Object>> dirs = new ArrayList<>();

        try {
            Path baseDir = uploadStorageResolver.getUploadRoot();
            Path targetDir = baseDir.resolve(path).normalize();

            // 安全检查：禁止超出 uploads 目录
            if (!targetDir.startsWith(baseDir)) {
                return ApiResult.fail("路径不合法");
            }

            if (!Files.exists(targetDir)) {
                return ApiResult.fail("目录不存在");
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
            result.put("currentPath", path);
            result.put("parentPath", getParentPath(path));
            result.put("total", all.size());

        } catch (Exception e) {
            return ApiResult.fail("读取文件列表失败: " + e.getMessage());
        }

        return ApiResult.ok(result);
    }

    @DeleteMapping
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
            Files.delete(target);
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
            if (!target.startsWith(baseDir) || !Files.exists(target)) {
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
    public ApiResult<Map<String, Object>> stats() {
        long now = System.currentTimeMillis();
        if (cachedStats != null && now - cachedStatsAt < STATS_CACHE_TTL_MS) {
            return ApiResult.ok(cachedStats);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            Path baseDir = uploadStorageResolver.getUploadRoot();
            long totalSize = 0L;
            long fileCount = 0L;
            if (Files.exists(baseDir)) {
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
            }
            stats.put("totalSize", totalSize);
            stats.put("fileCount", fileCount);
            stats.put("uploadDir", baseDir.toString());
            stats.put("totalSizeReadable", formatSize(totalSize));
        } catch (Exception e) {
            return ApiResult.fail("读取文件统计失败: " + e.getMessage());
        }
        cachedStats = stats;
        cachedStatsAt = now;
        return ApiResult.ok(stats);
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
}
