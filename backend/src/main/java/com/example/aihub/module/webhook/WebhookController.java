package com.example.aihub.module.webhook;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.infrastructure.service.WebhookService;
import com.example.aihub.infrastructure.vo.WebhookVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/webhooks")
@SaCheckLogin
@SaCheckRole("admin")
public class WebhookController {
    private final WebhookService webhookService;

    @GetMapping
    public ApiResult<PageResult<WebhookVO>> list(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "20") long pageSize) {
        PageResult<com.example.aihub.infrastructure.entity.Webhook> result = webhookService.page(page, pageSize);
        return ApiResult.ok(new PageResult<>(result.getTotal(), result.getPages(),
                result.getRecords().stream().map(this::toVO).collect(Collectors.toList())));
    }

    @GetMapping("/meta")
    public ApiResult<List<java.util.Map<String, String>>> meta() {
        return ApiResult.ok(webhookService.supportedEvents());
    }

    @GetMapping("/{id}")
    public ApiResult<WebhookVO> get(@PathVariable Long id) {
        return ApiResult.ok(toVO(webhookService.get(id)));
    }

    @PostMapping
    public ApiResult<WebhookVO> create(@RequestParam String name, @RequestParam String url,
                                       @RequestParam(defaultValue = "task.completed") String events,
                                       @RequestParam(required = false) String secret) {
        return ApiResult.ok(toVO(webhookService.create(name, url, events, secret)));
    }

    @PutMapping("/{id}")
    public ApiResult<WebhookVO> update(@PathVariable Long id, @RequestParam String name,
                                       @RequestParam String url, @RequestParam String events,
                                       @RequestParam(required = false) String secret,
                                       @RequestParam(required = false) Integer status) {
        return ApiResult.ok(toVO(webhookService.update(id, name, url, events, secret, status)));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        webhookService.delete(id);
        return ApiResult.ok();
    }

    private WebhookVO toVO(com.example.aihub.infrastructure.entity.Webhook webhook) {
        WebhookVO vo = new WebhookVO();
        vo.setId(webhook.getId());
        vo.setName(webhook.getName());
        vo.setUrl(webhook.getUrl());
        vo.setEvents(webhook.getEvents());
        vo.setStatus(webhook.getStatus());
        vo.setSecretConfigured(webhook.getSecret() != null && !webhook.getSecret().isBlank());
        vo.setCreatedAt(webhook.getCreatedAt());
        vo.setUpdatedAt(webhook.getUpdatedAt());
        return vo;
    }
}
