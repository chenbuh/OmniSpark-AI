package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/logs")
@SaCheckLogin
@SaCheckRole("admin")
public class LogViewerController {

    @Value("${logging.file.path:logs}")
    private String logPath;

    @GetMapping
    public ApiResult<Map<String, Object>> view(
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "app.log") String file) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> logLines = new ArrayList<>();

        try {
            Path logFile = Paths.get(logPath, file);
            if (!Files.exists(logFile)) {
                // 尝试查找最近的日志文件
                Path dir = Paths.get(logPath);
                if (Files.exists(dir)) {
                    try (var stream = Files.list(dir)) {
                        var files = stream.filter(f -> f.toString().endsWith(".log"))
                                .sorted((a, b) -> b.toString().compareTo(a.toString()))
                                .toList();
                        if (!files.isEmpty()) {
                            logFile = files.get(0);
                        }
                    }
                }
            }

            if (Files.exists(logFile)) {
                try (var reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
                    String line;
                    List<String> allLines = new ArrayList<>();
                    while ((line = reader.readLine()) != null) {
                        if (search == null || search.isBlank() || line.toLowerCase().contains(search.toLowerCase())) {
                            allLines.add(line);
                        }
                    }
                    // 取最后 N 行
                    int start = Math.max(0, allLines.size() - lines);
                    logLines = allLines.subList(start, allLines.size());
                }
            } else {
                logLines.add("日志文件不存在: " + logFile.toAbsolutePath());
                logLines.add("请确认日志文件路径配置: " + logPath);
            }
        } catch (Exception e) {
            logLines.add("读取日志失败: " + e.getMessage());
        }

        result.put("lines", logLines);
        result.put("total", logLines.size());
        result.put("file", logPath + "/" + file);
        return ApiResult.ok(result);
    }
}
