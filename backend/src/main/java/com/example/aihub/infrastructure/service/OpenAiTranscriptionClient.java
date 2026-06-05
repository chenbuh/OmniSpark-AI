package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OpenAiTranscriptionClient {
    private static final Duration TRANSCRIPTION_TIMEOUT = Duration.ofMinutes(8);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public String transcribe(String baseUrl,
                             String apiKey,
                             String modelName,
                             String language,
                             String prompt,
                             MediaFile mediaFile) {
        String endpoint = resolveEndpoint(baseUrl, "/audio/transcriptions");
        try {
            return requestSrt(endpoint, apiKey, modelName, language, prompt, mediaFile);
        } catch (BusinessException ex) {
            if (!shouldFallbackToVerboseJson(ex.getMessage())) {
                throw ex;
            }
        }
        return requestVerboseJson(endpoint, apiKey, modelName, language, prompt, mediaFile);
    }

    private String requestSrt(String endpoint,
                              String apiKey,
                              String modelName,
                              String language,
                              String prompt,
                              MediaFile mediaFile) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", modelName);
        fields.put("response_format", "srt");
        if (language != null && !language.isBlank()) {
            fields.put("language", language.trim());
        }
        if (prompt != null && !prompt.isBlank()) {
            fields.put("prompt", prompt.trim());
        }
        HttpResponse<byte[]> response = sendMultipart(endpoint, apiKey, fields, mediaFile);
        String contentType = response.headers().firstValue("content-type").orElse("");
        byte[] body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(extractErrorMessage(body, contentType, endpoint, response.statusCode()));
        }
        if (looksLikeHtml(body, contentType)) {
            throw new BusinessException(buildHtmlEndpointError(endpoint, response.statusCode(), new String(body, StandardCharsets.UTF_8)));
        }
        String srt = new String(body, StandardCharsets.UTF_8).trim();
        if (looksLikeSrt(srt)) {
            return normalizeSrt(srt);
        }
        throw new BusinessException("转写接口未返回 SRT 字幕");
    }

    private String requestVerboseJson(String endpoint,
                                      String apiKey,
                                      String modelName,
                                      String language,
                                      String prompt,
                                      MediaFile mediaFile) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", modelName);
        fields.put("response_format", "verbose_json");
        if (language != null && !language.isBlank()) {
            fields.put("language", language.trim());
        }
        if (prompt != null && !prompt.isBlank()) {
            fields.put("prompt", prompt.trim());
        }
        HttpResponse<byte[]> response = sendMultipart(endpoint, apiKey, fields, mediaFile);
        String contentType = response.headers().firstValue("content-type").orElse("");
        byte[] body = response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(extractErrorMessage(body, contentType, endpoint, response.statusCode()));
        }
        if (looksLikeHtml(body, contentType)) {
            throw new BusinessException(buildHtmlEndpointError(endpoint, response.statusCode(), new String(body, StandardCharsets.UTF_8)));
        }
        try {
            String text = new String(body, StandardCharsets.UTF_8).trim();
            JsonNode node = objectMapper.readTree(text);
            String srt = buildSrtFromVerboseJson(node);
            if (srt == null || srt.isBlank()) {
                throw new BusinessException("转写接口未返回可用字幕片段");
            }
            return srt;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析转写结果失败: " + ex.getMessage());
        }
    }

    private HttpResponse<byte[]> sendMultipart(String endpoint,
                                               String apiKey,
                                               Map<String, String> fields,
                                               MediaFile mediaFile) {
        try {
            String boundary = "----CodexBoundary" + UUID.randomUUID().toString().replace("-", "");
            List<byte[]> body = new ArrayList<>();
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                body.add(textPart(boundary, entry.getKey(), entry.getValue()));
            }
            body.add(filePart(boundary, "file", mediaFile.getFileName(), mediaFile.getMimeType(), mediaFile.getBytes()));
            body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(TRANSCRIPTION_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json,text/plain,text/srt,application/octet-stream")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (BusinessException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new BusinessException("语音转写超时，已等待 " + TRANSCRIPTION_TIMEOUT.toMinutes() + " 分钟");
        } catch (Exception ex) {
            throw new BusinessException("调用转写模型失败: " + ex.getMessage());
        }
    }

    private String buildSrtFromVerboseJson(JsonNode node) {
        JsonNode segments = node.path("segments");
        if (segments.isArray() && !segments.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            int index = 1;
            for (JsonNode segment : segments) {
                String text = segment.path("text").asText("").trim();
                if (text.isBlank()) {
                    continue;
                }
                double start = segment.path("start").asDouble(-1);
                double end = segment.path("end").asDouble(-1);
                if (start < 0 || end < start) {
                    continue;
                }
                builder.append(index++).append("\n");
                builder.append(formatSrtTimestamp(start))
                        .append(" --> ")
                        .append(formatSrtTimestamp(end))
                        .append("\n");
                builder.append(text).append("\n\n");
            }
            return builder.toString().trim();
        }

        String fullText = node.path("text").asText("").trim();
        double duration = node.path("duration").asDouble(-1);
        if (!fullText.isBlank() && duration > 0) {
            return "1\n"
                    + formatSrtTimestamp(0)
                    + " --> "
                    + formatSrtTimestamp(duration)
                    + "\n"
                    + fullText
                    + "\n";
        }
        return "";
    }

    private String formatSrtTimestamp(double seconds) {
        long totalMillis = Math.max(0L, Math.round(seconds * 1000));
        long hours = totalMillis / 3_600_000;
        long minutes = (totalMillis % 3_600_000) / 60_000;
        long secs = (totalMillis % 60_000) / 1000;
        long millis = totalMillis % 1000;
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, secs, millis);
    }

    private boolean looksLikeSrt(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String normalized = content.replace("\r\n", "\n").trim();
        return normalized.matches("(?s)^\\d+\\n\\d{2}:\\d{2}:\\d{2},\\d{3} --> \\d{2}:\\d{2}:\\d{2},\\d{3}\\n.+");
    }

    private String normalizeSrt(String content) {
        return content.replace("\r\n", "\n").trim() + "\n";
    }

    private boolean shouldFallbackToVerboseJson(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("response_format")
                || normalized.contains("verbose_json")
                || normalized.contains("unsupported")
                || normalized.contains("unknown parameter")
                || normalized.contains("invalid value")
                || normalized.contains("未返回 srt");
    }

    private String extractErrorMessage(byte[] body, String contentType, String endpoint, int statusCode) {
        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        if (looksLikeHtml(body, contentType)) {
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
        return text == null || text.isBlank() ? "转写接口返回错误" : text;
    }

    private boolean looksLikeHtml(byte[] body, String contentType) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (normalizedContentType.contains("text/html")) {
            return true;
        }
        if (body == null || body.length == 0) {
            return false;
        }
        String text = new String(body, 0, Math.min(body.length, 128), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        return text.startsWith("<!doctype html")
                || text.startsWith("<html")
                || text.startsWith("<body");
    }

    private String buildHtmlEndpointError(String url, int statusCode, String body) {
        if (body != null && body.toLowerCase(Locale.ROOT).contains("<title>nl-api</title>")) {
            return "模型服务返回了 nl-api 管理页 HTML（HTTP " + statusCode + "），当前 Base URL 很可能填成了站点地址而不是 OpenAI 接口地址: " + url;
        }
        return "模型服务返回了 HTML 页面（HTTP " + statusCode + "），请确认 Base URL 指向 OpenAI 兼容接口地址而不是网站首页: " + url;
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

    private byte[] textPart(String boundary, String name, String value) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] filePart(String boundary, String name, String fileName, String mimeType, byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: " + (mimeType == null || mimeType.isBlank() ? "application/octet-stream" : mimeType) + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(bytes);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (Exception ex) {
            throw new BusinessException("构建转写请求失败: " + ex.getMessage());
        }
    }

    @Data
    @AllArgsConstructor
    public static class MediaFile {
        private byte[] bytes;
        private String fileName;
        private String mimeType;
    }
}
