package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebhookService {
    private final WebhookMapper webhookMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<Webhook> list() {
        return webhookMapper.selectList(new LambdaQueryWrapper<Webhook>().orderByDesc(Webhook::getId));
    }

    public Webhook get(Long id) {
        Webhook wh = webhookMapper.selectById(id);
        if (wh == null) throw new BusinessException("Webhook 不存在");
        return wh;
    }

    @Transactional(rollbackFor = Exception.class)
    public Webhook create(String name, String url, String events, String secret) {
        Webhook wh = new Webhook();
        wh.setName(name);
        wh.setUrl(url);
        wh.setEvents(events != null ? events : "task.completed");
        wh.setSecret(secret);
        wh.setStatus(1);
        webhookMapper.insert(wh);
        return wh;
    }

    @Transactional(rollbackFor = Exception.class)
    public Webhook update(Long id, String name, String url, String events, String secret, Integer status) {
        Webhook wh = webhookMapper.selectById(id);
        if (wh == null) throw new BusinessException("Webhook 不存在");
        if (name != null) wh.setName(name);
        if (url != null) wh.setUrl(url);
        if (events != null) wh.setEvents(events);
        wh.setSecret(secret);
        if (status != null) wh.setStatus(status);
        webhookMapper.updateById(wh);
        return wh;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        webhookMapper.deleteById(id);
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
}
