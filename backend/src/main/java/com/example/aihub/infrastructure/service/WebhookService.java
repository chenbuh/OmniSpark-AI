package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.Webhook;
import com.example.aihub.infrastructure.mapper.WebhookMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private static final Set<String> SUPPORTED_EVENTS = Set.of("task.started", "task.completed", "task.failed");
    private final WebhookMapper webhookMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<Webhook> list(int limit) {
        return webhookMapper.selectList(new LambdaQueryWrapper<Webhook>()
                .orderByDesc(Webhook::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
    }

    public Webhook get(Long id) {
        Webhook wh = webhookMapper.selectById(id);
        if (wh == null) throw new BusinessException("Webhook 不存在");
        return wh;
    }

    @Transactional(rollbackFor = Exception.class)
    public Webhook create(String name, String url, String events, String secret) {
        Webhook wh = new Webhook();
        wh.setName(normalizeName(name));
        wh.setUrl(normalizeUrl(url));
        wh.setEvents(normalizeEvents(events));
        wh.setSecret(normalizeSecret(secret));
        wh.setStatus(1);
        webhookMapper.insert(wh);
        return wh;
    }

    @Transactional(rollbackFor = Exception.class)
    public Webhook update(Long id, String name, String url, String events, String secret, Integer status) {
        Webhook wh = webhookMapper.selectById(id);
        if (wh == null) throw new BusinessException("Webhook 不存在");
        if (name != null) wh.setName(normalizeName(name));
        if (url != null) wh.setUrl(normalizeUrl(url));
        if (events != null) wh.setEvents(normalizeEvents(events));
        if (secret != null) wh.setSecret(normalizeSecret(secret));
        if (status != null) wh.setStatus(normalizeStatus(status));
        webhookMapper.updateById(wh);
        return wh;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Webhook wh = webhookMapper.selectById(id);
        if (wh == null) {
            throw new BusinessException("Webhook 不存在");
        }
        webhookMapper.deleteById(id);
    }

    public List<Map<String, String>> supportedEvents() {
        List<Map<String, String>> result = new ArrayList<>();
        for (String event : SUPPORTED_EVENTS.stream()
                .sorted(Comparator.comparingInt(this::eventSortOrder))
                .toList()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", event);
            item.put("label", eventLabel(event));
            result.add(item);
        }
        return result;
    }

    /**
     * 触发所有匹配事件的 Webhook
     */
    public void trigger(String event, Long taskId, String taskType, String status, String prompt) {
        List<Webhook> hooks = webhookMapper.selectList(new LambdaQueryWrapper<Webhook>()
                .eq(Webhook::getStatus, 1)
                .like(Webhook::getEvents, event));
        for (Webhook wh : hooks) {
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("event", event);
                payload.put("taskId", taskId);
                payload.put("taskType", taskType);
                payload.put("status", status);
                payload.put("prompt", prompt);
                payload.put("timestamp", System.currentTimeMillis());

                String body = objectMapper.writeValueAsString(payload);
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(wh.getUrl()))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json");
                if (wh.getSecret() != null && !wh.getSecret().isBlank()) {
                    builder.header("X-Webhook-Secret", wh.getSecret());
                }
                HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
            } catch (Exception ignored) {}
        }
    }

    private String normalizeEvents(String events) {
        List<String> normalized = parseEvents(events);
        if (normalized.isEmpty()) {
            throw new BusinessException("至少选择一个受支持的事件");
        }
        return String.join(",", normalized);
    }

    private String normalizeName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("Webhook 名称不能为空");
        }
        return normalized;
    }

    private String normalizeUrl(String url) {
        String normalized = url == null ? "" : url.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("Webhook 回调地址不能为空");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().trim().toLowerCase(java.util.Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw new BusinessException("Webhook 回调地址仅支持 http 或 https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new BusinessException("Webhook 回调地址缺少主机名");
            }
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Webhook 回调地址格式无效");
        }
    }

    private String normalizeSecret(String secret) {
        return secret == null ? "" : secret.trim();
    }

    private Integer normalizeStatus(Integer status) {
        if (status == null) {
            return null;
        }
        if (status != 0 && status != 1) {
            throw new BusinessException("Webhook 状态无效");
        }
        return status;
    }

    private List<String> parseEvents(String events) {
        if (events == null || events.isBlank()) {
            return List.of("task.completed");
        }
        return java.util.Arrays.stream(events.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .peek(item -> {
                    if (!SUPPORTED_EVENTS.contains(item)) {
                        throw new BusinessException("不支持的 Webhook 事件: " + item);
                    }
                })
                .sorted(Comparator.comparingInt(this::eventSortOrder))
                .toList();
    }

    private int eventSortOrder(String event) {
        return switch (event) {
            case "task.started" -> 10;
            case "task.completed" -> 20;
            case "task.failed" -> 30;
            default -> 100;
        };
    }

    private String eventLabel(String event) {
        return switch (event) {
            case "task.started" -> "任务开始";
            case "task.completed" -> "任务完成";
            case "task.failed" -> "任务失败";
            default -> event;
        };
    }
}
