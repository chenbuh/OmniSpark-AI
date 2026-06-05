package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenAiSpeechClient {
    private static final Duration SPEECH_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration MEDIA_DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public SynthesizedAudio synthesize(String baseUrl,
                                       String apiKey,
                                       String modelName,
                                       String input,
                                       SpeechOptions options) {
        try {
            String endpoint = resolveEndpoint(baseUrl, "/audio/speech");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", modelName);
            payload.put("input", input);
            payload.put("voice", normalizeVoice(options.getVoice()));
            if (options.getResponseFormat() != null && !options.getResponseFormat().isBlank()) {
                payload.put("response_format", options.getResponseFormat().trim().toLowerCase(Locale.ROOT));
            }
            if (options.getSpeed() != null) {
                payload.put("speed", options.getSpeed());
            }
            if (options.getInstructions() != null && !options.getInstructions().isBlank()) {
                payload.put("instructions", options.getInstructions().trim());
            }

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(SPEECH_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "audio/mpeg,audio/*,application/octet-stream,application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<byte[]> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            } catch (java.net.http.HttpTimeoutException ex) {
                throw new BusinessException("语音模型响应超时，已等待 " + SPEECH_TIMEOUT.toMinutes() + " 分钟");
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            byte[] body = response.body();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(extractErrorMessage(body, contentType, endpoint, response.statusCode()));
            }
            if (looksLikeAudio(contentType, body)) {
                String mimeType = normalizeMimeType(contentType, options.getResponseFormat());
                return new SynthesizedAudio(body, mimeType, extensionFromMimeType(mimeType));
            }
            return extractAudioFromJson(body, contentType, options.getResponseFormat());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("调用语音模型失败: " + ex.getMessage());
        }
    }

    private SynthesizedAudio extractAudioFromJson(byte[] body, String contentType, String responseFormat) throws Exception {
        String text = new String(body, StandardCharsets.UTF_8);
        if (looksLikeHtml(text, contentType)) {
            throw new BusinessException(buildHtmlEndpointError("语音接口", 200, text));
        }
        JsonNode node = objectMapper.readTree(text);
        if (node.hasNonNull("b64_json")) {
            byte[] audioBytes = Base64.getDecoder().decode(node.get("b64_json").asText());
            String mimeType = normalizeMimeType(node.path("mime_type").asText(null), responseFormat);
            return new SynthesizedAudio(audioBytes, mimeType, extensionFromMimeType(mimeType));
        }
        if (node.hasNonNull("audio")) {
            byte[] audioBytes = Base64.getDecoder().decode(node.get("audio").asText());
            String mimeType = normalizeMimeType(node.path("mime_type").asText(null), responseFormat);
            return new SynthesizedAudio(audioBytes, mimeType, extensionFromMimeType(mimeType));
        }
        JsonNode data = node.path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode first = data.get(0);
            if (first.hasNonNull("b64_json")) {
                byte[] audioBytes = Base64.getDecoder().decode(first.get("b64_json").asText());
                String mimeType = normalizeMimeType(first.path("mime_type").asText(null), responseFormat);
                return new SynthesizedAudio(audioBytes, mimeType, extensionFromMimeType(mimeType));
            }
            if (first.hasNonNull("url")) {
                return downloadRemoteAudio(first.get("url").asText(), normalizeMimeType(first.path("mime_type").asText(null), responseFormat));
            }
        }
        if (node.hasNonNull("url")) {
            return downloadRemoteAudio(node.get("url").asText(), normalizeMimeType(node.path("mime_type").asText(null), responseFormat));
        }
        throw new BusinessException("语音接口未返回可用音频数据");
    }

    private SynthesizedAudio downloadRemoteAudio(String url, String fallbackMimeType) throws Exception {
        com.example.aihub.common.security.SsrfGuard.validate(url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(MEDIA_DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException("下载语音结果失败: HTTP " + response.statusCode());
        }
        String mimeType = normalizeMimeType(response.headers().firstValue("content-type").orElse(null), fallbackMimeType);
        return new SynthesizedAudio(response.body(), mimeType, extensionFromMimeType(mimeType));
    }

    private boolean looksLikeAudio(String contentType, byte[] body) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.startsWith("audio/")) {
            return true;
        }
        if (normalizedContentType.contains("application/octet-stream")) {
            if (looksLikeStructuredText(body)) {
                return false;
            }
            return hasKnownAudioHeader(body);
        }
        return hasKnownAudioHeader(body);
    }

    private boolean hasKnownAudioHeader(byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }
        String header = new String(body, 0, Math.min(body.length, 24), StandardCharsets.ISO_8859_1);
        if (header.startsWith("ID3")
                || header.startsWith("RIFF")
                || header.startsWith("OggS")
                || header.startsWith("fLaC")) {
            return true;
        }
        return body.length >= 2
                && (body[0] & 0xFF) == 0xFF
                && ((body[1] & 0xE0) == 0xE0);
    }

    private boolean looksLikeStructuredText(byte[] body) {
        if (body == null || body.length == 0) {
            return false;
        }
        String prefix = new String(body, 0, Math.min(body.length, 32), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        return prefix.startsWith("{")
                || prefix.startsWith("[")
                || prefix.startsWith("<!doctype html")
                || prefix.startsWith("<html")
                || prefix.startsWith("<body");
    }

    private String extractErrorMessage(byte[] body, String contentType, String endpoint, int statusCode) {
        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        if (looksLikeHtml(text, contentType)) {
            return buildHtmlEndpointError(endpoint, statusCode, text);
        }
        try {
            JsonNode node = objectMapper.readTree(text);
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
        return text == null || text.isBlank() ? "语音接口返回错误" : text;
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

    private String normalizeVoice(String voice) {
        if (voice == null || voice.isBlank()) {
            return "alloy";
        }
        return voice.trim();
    }

    private String normalizeMimeType(String contentType, String fallbackFormat) {
        if (contentType != null && !contentType.isBlank()) {
            String value = contentType.toLowerCase(Locale.ROOT);
            if (value.contains("audio/mpeg")) return "audio/mpeg";
            if (value.contains("audio/mp3")) return "audio/mpeg";
            if (value.contains("audio/wav") || value.contains("audio/x-wav")) return "audio/wav";
            if (value.contains("audio/ogg")) return "audio/ogg";
            if (value.contains("audio/flac")) return "audio/flac";
            if (value.contains("audio/aac")) return "audio/aac";
            if (value.startsWith("audio/")) return value.split(";")[0].trim();
        }
        String normalizedFormat = fallbackFormat == null ? "" : fallbackFormat.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedFormat) {
            case "wav", "pcm" -> "audio/wav";
            case "ogg", "opus" -> "audio/ogg";
            case "flac" -> "audio/flac";
            case "aac" -> "audio/aac";
            default -> "audio/mpeg";
        };
    }

    private String extensionFromMimeType(String mimeType) {
        String lower = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("wav")) return "wav";
        if (lower.contains("ogg")) return "ogg";
        if (lower.contains("flac")) return "flac";
        if (lower.contains("aac")) return "aac";
        return "mp3";
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
            return "模型服务返回了 nl-api 管理页 HTML（HTTP " + statusCode + "），当前 Base URL 很可能填成了站点地址而不是 OpenAI 接口地址: " + url;
        }
        return "模型服务返回了 HTML 页面（HTTP " + statusCode + "），请确认 Base URL 指向 OpenAI 兼容接口地址而不是网站首页: " + url;
    }

    @Data
    @AllArgsConstructor
    public static class SpeechOptions {
        private String voice;
        private String responseFormat;
        private Double speed;
        private String instructions;
    }

    @Data
    @AllArgsConstructor
    public static class SynthesizedAudio {
        private byte[] bytes;
        private String mimeType;
        private String extension;
    }
}
