package com.example.aihub.module.webhook;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.infrastructure.entity.Webhook;
import com.example.aihub.infrastructure.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/webhooks")
@SaCheckLogin
@SaCheckRole("admin")
public class WebhookController {
    private final WebhookService webhookService;

    @GetMapping
    public ApiResult<PageResult<Webhook>> list(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResult.ok(webhookService.page(page, pageSize));
    }

    @GetMapping("/meta")
    public ApiResult<List<java.util.Map<String, String>>> meta() {
        return ApiResult.ok(webhookService.supportedEvents());
    }

    @GetMapping("/{id}")
    public ApiResult<Webhook> get(@PathVariable Long id) {
        return ApiResult.ok(webhookService.get(id));
    }

    @PostMapping
    public ApiResult<Webhook> create(@RequestParam String name, @RequestParam String url,
                                      @RequestParam(defaultValue = "task.completed") String events,
                                      @RequestParam(required = false) String secret) {
        return ApiResult.ok(webhookService.create(name, url, events, secret));
    }

    @PutMapping("/{id}")
    public ApiResult<Webhook> update(@PathVariable Long id, @RequestParam String name,
                                      @RequestParam String url, @RequestParam String events,
                                      @RequestParam(required = false) String secret,
                                      @RequestParam(required = false) Integer status) {
        return ApiResult.ok(webhookService.update(id, name, url, events, secret, status));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id) {
        webhookService.delete(id);
        return ApiResult.ok();
    }
}
