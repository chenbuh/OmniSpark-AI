package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/monitor")
@SaCheckLogin
@SaCheckRole("admin")
public class MonitorController {
    private static final long CACHE_TTL_MS = TimeUnit.SECONDS.toMillis(5);
    private volatile Map<String, Object> cachedMonitor;
    private volatile long cachedAt;

    @GetMapping
    public ApiResult<Map<String, Object>> monitor() {
        long now = System.currentTimeMillis();
        if (cachedMonitor != null && now - cachedAt < CACHE_TTL_MS) {
            return ApiResult.ok(cachedMonitor);
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // CPU
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        result.put("cpu", Map.of(
            "availableProcessors", osBean.getAvailableProcessors(),
            "systemLoadAverage", osBean.getSystemLoadAverage(),
            "osName", osBean.getName(),
            "osArch", osBean.getArch()
        ));

        // 尝试获取 CPU 使用率 (JDK 17+)
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            result.put("cpuUsage", Math.round(sunOsBean.getCpuLoad() * 10000) / 100.0);
            result.put("processCpuUsage", Math.round(sunOsBean.getProcessCpuLoad() * 10000) / 100.0);
        }

        // 内存
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        result.put("memory", Map.of(
            "heapUsed", heapUsed,
            "heapMax", heapMax,
            "heapUsedReadable", formatBytes(heapUsed),
            "heapMaxReadable", formatBytes(heapMax),
            "heapUsagePercent", heapMax > 0 ? Math.round((double) heapUsed / heapMax * 10000) / 100.0 : 0,
            "nonHeapUsed", memBean.getNonHeapMemoryUsage().getUsed(),
            "nonHeapMax", memBean.getNonHeapMemoryUsage().getMax()
        ));

        // JVM
        RuntimeMXBean jvmBean = ManagementFactory.getRuntimeMXBean();
        result.put("jvm", Map.of(
            "uptime", jvmBean.getUptime(),
            "uptimeReadable", formatUptime(jvmBean.getUptime()),
            "vmName", jvmBean.getVmName(),
            "vmVersion", jvmBean.getVmVersion()
        ));

        // 磁盘
        File[] roots = File.listRoots();
        var disks = new java.util.ArrayList<Map<String, Object>>();
        long totalDisk = 0, freeDisk = 0;
        for (File root : roots) {
            long total = root.getTotalSpace();
            long free = root.getFreeSpace();
            totalDisk += total;
            freeDisk += free;
            disks.add(Map.of(
                "path", root.getPath(),
                "total", total,
                "free", free,
                "used", total - free,
                "totalReadable", formatBytes(total),
                "freeReadable", formatBytes(free),
                "usedReadable", formatBytes(total - free),
                "usagePercent", total > 0 ? Math.round((double)(total - free) / total * 10000) / 100.0 : 0
            ));
        }
        result.put("disks", disks);
        result.put("diskTotal", totalDisk);
        result.put("diskFree", freeDisk);
        result.put("diskUsed", totalDisk - freeDisk);
        result.put("diskUsagePercent", totalDisk > 0 ? Math.round((double)(totalDisk - freeDisk) / totalDisk * 10000) / 100.0 : 0);

        cachedMonitor = result;
        cachedAt = now;
        return ApiResult.ok(result);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatUptime(long ms) {
        long days = ms / 86400000;
        long hours = (ms % 86400000) / 3600000;
        long minutes = (ms % 3600000) / 60000;
        return days + "天 " + hours + "小时 " + minutes + "分钟";
    }
}
