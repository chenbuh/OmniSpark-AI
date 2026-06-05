package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiTextClient {
    private static final Duration TEXT_TIMEOUT = Duration.ofSeconds(90);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public String optimizeImagePrompt(String baseUrl,
                                      String apiKey,
                                      String modelName,
                                      String prompt) {
        try {
            String endpoint = resolveEndpoint(baseUrl, "/chat/completions");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", modelName);
            payload.put("temperature", 0.7);
            payload.put("max_tokens", 400);
            payload.put("messages", List.of(
                    Map.of(
                            "role", "system",
                            "content", "你是一名专业的 AI 生图提示词优化助手。请在不改变用户核心意图的前提下，补全主体、环境、镜头、光线、材质、构图和细节。尽量保留原始语言与关键词；若原提示词已较完整，只做轻量润色。只返回一条优化后的提示词正文，不要解释，不要加引号，不要输出 Markdown。"
                    ),
                    Map.of(
                            "role", "user",
                            "content", "请润色这条用于 AI 生图的提示词，并直接返回优化后的提示词：\n" + prompt.trim()
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(TEXT_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (java.net.http.HttpTimeoutException ex) {
                throw new BusinessException("提示词润色超时，请稍后重试");
            }

            String body = response.body();
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                if (looksLikeHtml(body, contentType)) {
                    throw new BusinessException(buildHtmlEndpointError(endpoint, response.statusCode(), body));
                }
                throw new BusinessException(extractErrorMessage(body));
            }
            if (looksLikeHtml(body, contentType)) {
                throw new BusinessException(buildHtmlEndpointError(endpoint, response.statusCode(), body));
            }

            JsonNode node = objectMapper.readTree(body);
            String content = extractText(node);
            if (content == null || content.isBlank()) {
                throw new BusinessException("提示词润色接口未返回可用文本，请确认当前模型支持聊天补全");
            }
            return content.trim();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("调用提示词润色模型失败: " + ex.getMessage());
        }
    }

    private String extractText(JsonNode node) {
        JsonNode choices = node.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode first = choices.get(0);
            String content = extractMessageContent(first.path("message").path("content"));
            if (content != null && !content.isBlank()) {
                return content;
            }
            String text = first.path("text").asText("");
            if (!text.isBlank()) {
                return text;
            }
        }
        String message = node.path("message").asText("");
        if (!message.isBlank()) {
            return message;
        }
        return null;
    }

    private String extractMessageContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (!contentNode.isArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode part : contentNode) {
            String text = part.path("text").asText("");
            if (!text.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append('\n');
                }
                builder.append(text.trim());
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private String resolveEndpoint(String baseUrl, String suffix) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String endpoint = trimmed.endsWith(suffix) ? trimmed : trimmed + suffix;
        com.example.aihub.common.security.SsrfGuard.validate(endpoint);
        return endpoint;
    }

    private String extractErrorMessage(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            JsonNode error = node.path("error");
            if (!error.isMissingNode()) {
                String message = error.path("message").asText();
                if (!message.isBlank()) {
                    return message;
                }
            }
            String message = node.path("message").asText();
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
        }
        return body == null || body.isBlank() ? "远程文本接口返回错误" : body;
    }

    private boolean looksLikeHtml(String body, String contentType) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.contains("text/html")) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String trimmed = body.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("<!doctype html")
                || trimmed.startsWith("<html")
                || trimmed.startsWith("<body")
                || trimmed.startsWith("<?xml");
    }

    private String buildHtmlEndpointError(String url, int statusCode, String body) {
        if (body != null && body.toLowerCase(Locale.ROOT).contains("<title>nl-api</title>")) {
            return "文本模型服务返回了 nl-api 管理页 HTML（HTTP " + statusCode + "），当前 Base URL 很可能填成了站点地址而不是 OpenAI 接口地址: " + url;
        }
        return "文本模型服务返回了 HTML 页面（HTTP " + statusCode + "），请确认 Base URL 指向接口而不是网站首页: " + url;
    }
}
