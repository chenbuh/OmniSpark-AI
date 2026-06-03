package com.example.aihub.infrastructure.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.aihub.common.storage.UploadStorageResolver;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

@Component
@Slf4j
@RequiredArgsConstructor
public class OpenAiImageClient {
    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(10);
    /** 单次 HTTP 请求超时:网关无响应时尽快超时以触发换渠道重试,避免单次请求挂满 10 分钟。 */
    private static final Duration DEFAULT_SINGLE_REQUEST_TIMEOUT = Duration.ofSeconds(120);
    /** 免费网关在高峰期首包明显更慢,适当放宽单次等待时间,避免刚要出图就被本地超时切断。 */
    private static final Duration SLOW_PROVIDER_REQUEST_TIMEOUT = Duration.ofSeconds(240);
    private static final Duration MEDIA_DOWNLOAD_CONNECT_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration MEDIA_DOWNLOAD_READ_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration REFERENCE_DOWNLOAD_TIMEOUT = Duration.ofMinutes(2);
    private static final int MAX_SEND_ATTEMPTS = 5;
    private static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    private final ObjectMapper objectMapper;
    private final UploadStorageResolver uploadStorageResolver;

    public List<RenderedMedia> generateImage(String baseUrl,
                                             String apiKey,
                                             String modelName,
                                             String prompt,
                                             Integer count,
                                             String requestSize,
                                             String targetSize,
                                             String quality,
                                             List<ReferenceImage> references) {
        if (references != null && !references.isEmpty()) {
            return editImage(baseUrl, apiKey, modelName, prompt, count, requestSize, targetSize, quality, references.get(0));
        }
        return createImage(baseUrl, apiKey, modelName, prompt, count, requestSize, targetSize, quality);
    }

    public List<RenderedMedia> generateImageInpaint(String baseUrl,
                                                    String apiKey,
                                                    String modelName,
                                                    String prompt,
                                                    Integer count,
                                                    String requestSize,
                                                    String targetSize,
                                                    ReferenceImage image,
                                                    ReferenceImage mask) {
        return inpaintImage(baseUrl, apiKey, modelName, prompt, count, requestSize, targetSize, image, mask);
    }

    private List<RenderedMedia> createImage(String baseUrl,
                                            String apiKey,
                                            String modelName,
                                            String prompt,
                                            Integer count,
                                            String requestSize,
                                            String targetSize,
                                            String quality) {
        String endpoint = resolveEndpoint(baseUrl, "/images/generations");
        int actualCount = count == null || count < 1 ? 1 : count;
        Duration requestTimeout = resolveRequestTimeout(endpoint, modelName);
        JsonNode node = requestImageNode(endpoint, apiKey, modelName, prompt, actualCount, requestSize, quality, requestTimeout);
        try {
            return extractMedia(node, targetSize);
        } catch (IllegalStateException ex) {
            if (!shouldRetryWithInlineImageData(ex)) {
                throw ex;
            }
            try {
                JsonNode fallbackNode = postJson(
                        endpoint,
                        apiKey,
                        buildImagePayload(modelName, prompt, actualCount, requestSize, quality, false, true),
                        requestTimeout
                );
                return extractMedia(fallbackNode, targetSize);
            } catch (IllegalStateException fallbackEx) {
                throw explainOriginalAssetUnavailable(fallbackEx, ex);
            }
        }
    }

    private JsonNode requestImageNode(String endpoint,
                                      String apiKey,
                                      String modelName,
                                      String prompt,
                                      int actualCount,
                                      String requestSize,
                                      String quality,
                                      Duration requestTimeout) {
        try {
            return postJson(endpoint, apiKey, buildImagePayload(modelName, prompt, actualCount, requestSize, quality, true, false), requestTimeout);
        } catch (IllegalStateException ex) {
            if (shouldRetryWithMinimalPayload(ex)) {
                return requestImageNodeWithMinimalPayload(endpoint, apiKey, modelName, prompt, actualCount, requestSize, requestTimeout);
            }
            if (shouldRetryWithoutUrlResponse(ex)) {
                try {
                    return postJson(endpoint, apiKey, buildImagePayload(modelName, prompt, actualCount, requestSize, quality, false, false), requestTimeout);
                } catch (IllegalStateException legacyEx) {
                    if (shouldRetryWithMinimalPayload(legacyEx)) {
                        return requestImageNodeWithMinimalPayload(endpoint, apiKey, modelName, prompt, actualCount, requestSize, requestTimeout);
                    }
                    if (!shouldRetryWithCompatPayload(legacyEx)) {
                        throw legacyEx;
                    }
                    return postJson(endpoint, apiKey, buildImagePayload(modelName, prompt, actualCount, requestSize, quality, false, true), requestTimeout);
                }
            }
            if (!shouldRetryWithCompatPayload(ex)) {
                throw ex;
            }
            return postJson(endpoint, apiKey, buildImagePayload(modelName, prompt, actualCount, requestSize, quality, false, true), requestTimeout);
        }
    }

    private JsonNode requestImageNodeWithMinimalPayload(String endpoint,
                                                        String apiKey,
                                                        String modelName,
                                                        String prompt,
                                                        int actualCount,
                                                        String requestSize,
                                                        Duration requestTimeout) {
        log.warn("图片生成接口返回疑似供应商兼容错误,改用最小 JSON 请求体重试");
        try {
            return postJson(endpoint, apiKey, buildMinimalImagePayload(modelName, prompt, actualCount, requestSize, true, false), requestTimeout);
        } catch (IllegalStateException urlEx) {
            if (shouldRetryWithoutUrlResponse(urlEx) || shouldRetryWithMinimalPayload(urlEx)) {
                try {
                    return postJson(endpoint, apiKey, buildMinimalImagePayload(modelName, prompt, actualCount, requestSize, false, false), requestTimeout);
                } catch (IllegalStateException plainEx) {
                    if (!shouldRetryWithInlineImageData(plainEx) && !shouldRetryWithCompatPayload(plainEx)) {
                        throw plainEx;
                    }
                    return postJson(endpoint, apiKey, buildMinimalImagePayload(modelName, prompt, actualCount, requestSize, false, true), requestTimeout);
                }
            }
            if (!shouldRetryWithInlineImageData(urlEx) && !shouldRetryWithCompatPayload(urlEx)) {
                throw urlEx;
            }
            return postJson(endpoint, apiKey, buildMinimalImagePayload(modelName, prompt, actualCount, requestSize, false, true), requestTimeout);
        }
    }

    private Map<String, Object> buildImagePayload(String modelName,
                                                  String prompt,
                                                  int actualCount,
                                                  String requestSize,
                                                  String quality,
                                                  boolean preferUrlResponse,
                                                  boolean forceBase64Response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("prompt", prompt);
        payload.put("n", actualCount);
        payload.put("size", requestSize);
        if (supportsQualityParameter(modelName) && quality != null && !quality.isBlank()) {
            payload.put("quality", quality);
        }
        payload.put("output_format", forceBase64Response ? "jpeg" : "png");
        payload.put("background", "auto");
        if (forceBase64Response) {
            payload.put("response_format", "b64_json");
        } else if (preferUrlResponse) {
            payload.put("response_format", "url");
        }
        return payload;
    }

    private Map<String, Object> buildMinimalImagePayload(String modelName,
                                                         String prompt,
                                                         int actualCount,
                                                         String requestSize,
                                                         boolean preferUrlResponse,
                                                         boolean forceBase64Response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("prompt", prompt);
        payload.put("n", actualCount);
        payload.put("size", requestSize);
        if (forceBase64Response) {
            payload.put("response_format", "b64_json");
        } else if (preferUrlResponse) {
            payload.put("response_format", "url");
        }
        return payload;
    }

    private List<RenderedMedia> inpaintImage(String baseUrl,
                                             String apiKey,
                                             String modelName,
                                             String prompt,
                                             Integer count,
                                             String requestSize,
                                             String targetSize,
                                             ReferenceImage referenceImage,
                                             ReferenceImage maskImage) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", modelName);
        fields.put("prompt", prompt);
        fields.put("n", String.valueOf(count == null || count < 1 ? 1 : count));
        fields.put("size", requestSize);
        fields.put("output_format", "png");
        fields.put("response_format", "url");

        List<byte[]> body = new ArrayList<>();
        String boundary = "----CodexBoundary" + UUID.randomUUID().toString().replace("-", "");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            body.add(textPart(boundary, entry.getKey(), entry.getValue()));
        }
        body.add(filePart(boundary, "image", referenceImage.getFileName(), referenceImage.getMimeType(), referenceImage.getBytes()));
        if (maskImage != null) {
            body.add(filePart(boundary, "mask", maskImage.getFileName(), maskImage.getMimeType(), maskImage.getBytes()));
        }
        body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        String endpoint = resolveEndpoint(baseUrl, "/images/edits");
        Duration requestTimeout = resolveRequestTimeout(endpoint, modelName);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                    .build();
            JsonNode node = send(request);
            try {
                return extractMedia(node, targetSize);
            } catch (IllegalStateException ex) {
                if (!shouldRetryWithInlineImageData(ex)) {
                    throw ex;
                }
                try {
                    fields.put("response_format", "b64_json");
                    fields.put("output_format", "jpeg");
                    return sendMultipartEdit(endpoint, apiKey, fields, referenceImage, maskImage, targetSize, requestTimeout);
                } catch (IllegalStateException fallbackEx) {
                    throw explainOriginalAssetUnavailable(fallbackEx, ex);
                }
            }
        } catch (IllegalStateException ex) {
            if (!shouldRetryWithoutUrlResponse(ex)) {
                throw ex;
            }
            fields.remove("response_format");
            return sendMultipartEdit(endpoint, apiKey, fields, referenceImage, maskImage, targetSize, requestTimeout);
        }
    }

    private List<RenderedMedia> editImage(String baseUrl,
                                          String apiKey,
                                          String modelName,
                                          String prompt,
                                          Integer count,
                                          String requestSize,
                                          String targetSize,
                                          String quality,
                                          ReferenceImage referenceImage) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", modelName);
        fields.put("prompt", prompt);
        fields.put("n", String.valueOf(count == null || count < 1 ? 1 : count));
        fields.put("size", requestSize);
        if (supportsQualityParameter(modelName) && quality != null && !quality.isBlank()) {
            fields.put("quality", quality);
        }
        fields.put("output_format", "png");
        fields.put("background", "auto");
        fields.put("response_format", "url");

        List<byte[]> body = new ArrayList<>();
        String boundary = "----CodexBoundary" + UUID.randomUUID().toString().replace("-", "");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            body.add(textPart(boundary, entry.getKey(), entry.getValue()));
        }
        body.add(filePart(boundary, "image", referenceImage.getFileName(), referenceImage.getMimeType(), referenceImage.getBytes()));
        body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        String endpoint = resolveEndpoint(baseUrl, "/images/edits");
        Duration requestTimeout = resolveRequestTimeout(endpoint, modelName);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                    .build();
            JsonNode node = send(request);
            try {
                return extractMedia(node, targetSize);
            } catch (IllegalStateException ex) {
                if (!shouldRetryWithInlineImageData(ex)) {
                    throw ex;
                }
                try {
                    fields.put("response_format", "b64_json");
                    fields.put("output_format", "jpeg");
                    return sendMultipartEdit(endpoint, apiKey, fields, referenceImage, null, targetSize, requestTimeout);
                } catch (IllegalStateException fallbackEx) {
                    throw explainOriginalAssetUnavailable(fallbackEx, ex);
                }
            }
        } catch (IllegalStateException ex) {
            if (!shouldRetryWithoutUrlResponse(ex)) {
                throw ex;
            }
            fields.remove("response_format");
            return sendMultipartEdit(endpoint, apiKey, fields, referenceImage, null, targetSize, requestTimeout);
        }
    }

    private List<RenderedMedia> sendMultipartEdit(String endpoint,
                                                  String apiKey,
                                                  Map<String, String> fields,
                                                  ReferenceImage referenceImage,
                                                  ReferenceImage maskImage,
                                                  String targetSize,
                                                  Duration requestTimeout) {
        List<byte[]> body = new ArrayList<>();
        String boundary = "----CodexBoundary" + UUID.randomUUID().toString().replace("-", "");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            body.add(textPart(boundary, entry.getKey(), entry.getValue()));
        }
        body.add(filePart(boundary, "image", referenceImage.getFileName(), referenceImage.getMimeType(), referenceImage.getBytes()));
        if (maskImage != null) {
            body.add(filePart(boundary, "mask", maskImage.getFileName(), maskImage.getMimeType(), maskImage.getBytes()));
        }
        body.add(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(body))
                .build();
        return extractMedia(send(request), targetSize);
    }

    private JsonNode postJson(String url, String apiKey, Map<String, Object> payload, Duration requestTimeout) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            return send(request);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("调用图片生成接口失败: " + ex.getMessage(), ex);
        }
    }

    private JsonNode send(HttpRequest request) {
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                String contentType = response.headers().firstValue("content-type").orElse("");
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    if (looksLikeHtml(body, contentType)) {
                        throw new IllegalStateException(buildHtmlEndpointError(request.uri().toString(), response.statusCode(), body));
                    }
                    throw new IllegalStateException(extractErrorMessage(body));
                }
                if (looksLikeHtml(body, contentType)) {
                    throw new IllegalStateException(buildHtmlEndpointError(request.uri().toString(), response.statusCode(), body));
                }
                if (body == null || body.isBlank()) {
                    throw new IllegalStateException("模型服务返回空响应");
                }
                try {
                    return objectMapper.readTree(body);
                } catch (Exception parseEx) {
                    throw new IllegalStateException("模型服务返回的不是有效 JSON: " + abbreviateBody(body), parseEx);
                }
            } catch (Exception ex) {
                if (shouldRetryRequestException(ex, attempt)) {
                    // 瞬时网关/网络错误,退避后重试。网关"可用渠道不存在"类错误需要更长等待,
                    // 给网关切换到健康渠道的时间(实测该免费网关切换需数十秒)。
                    log.warn("图片生成请求第 {}/{} 次失败,将退避重试: {}", attempt, MAX_SEND_ATTEMPTS, ex.getMessage());
                    sleepBeforeRetry(attempt, ex);
                    continue;
                }
                if (ex instanceof IllegalStateException) {
                    throw (IllegalStateException) ex;
                }
                throw new IllegalStateException(describeRequestException(ex), ex);
            }
        }
        throw new IllegalStateException("调用图片生成接口失败: 已重试多次仍未成功");
    }

    private String describeRequestException(Exception ex) {
        if (ex instanceof java.net.http.HttpTimeoutException) {
            return "调用图片生成接口失败: 模型服务响应超时，已等待 " + GENERATION_TIMEOUT.toMinutes() + " 分钟";
        }
        return "调用图片生成接口失败: " + ex.getMessage();
    }

    private HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private Duration resolveRequestTimeout(String endpoint, String modelName) {
        String normalizedEndpoint = endpoint == null ? "" : endpoint.toLowerCase(Locale.ROOT);
        String normalizedModel = modelName == null ? "" : modelName.toLowerCase(Locale.ROOT);
        if (normalizedEndpoint.contains("tcloudbase.com")
                || normalizedModel.contains("flatfee")) {
            return SLOW_PROVIDER_REQUEST_TIMEOUT;
        }
        return DEFAULT_SINGLE_REQUEST_TIMEOUT;
    }

    /**
     * 重试前的退避等待。网关"可用渠道不存在"类瞬时错误需要较长等待(8/16/24...秒,上限30秒),
     * 给网关切换到健康渠道的时间;普通网络错误用较短等待(2/4/6...秒,上限10秒)。
     */
    private void sleepBeforeRetry(int attempt, Exception ex) {
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        long waitMs;
        if (isRetryableGatewayError(msg)) {
            waitMs = Math.min(attempt * 8L, 30L) * 1000L;
        } else {
            waitMs = Math.min(attempt * 2L, 10L) * 1000L;
        }
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean shouldRetryRequestException(Exception ex, int attempt) {
        if (attempt >= MAX_SEND_ATTEMPTS) {
            return false;
        }
        if (ex instanceof java.net.http.HttpTimeoutException || ex instanceof java.net.ConnectException) {
            return true;
        }
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("header parser received no bytes")
                || normalized.contains("connection reset")
                || normalized.contains("http connect timed out")
                || normalized.contains("unexpected end of file")
                || normalized.contains("prematurely reached end of stream")
                || normalized.contains("返回空响应")
                || normalized.contains("空响应体")
                || normalized.contains("empty response")
                || isRetryableGatewayError(normalized);
    }

    /**
     * 识别 OpenAI 兼容网关(new-api / one-api 等)的瞬时渠道错误。
     * 这类错误网关通常会带"（retry）"提示,重试时网关会切换到其它可用渠道,往往能成功,
     * 因此不应在首次失败就直接判定任务失败。
     */
    private boolean isRetryableGatewayError(String normalizedMessage) {
        return normalizedMessage.contains("（retry）")
                || normalizedMessage.contains("(retry)")
                || normalizedMessage.contains("可用渠道")
                || normalizedMessage.contains("无可用渠道")
                || normalizedMessage.contains("渠道不存在")
                || normalizedMessage.contains("no available channel")
                || normalizedMessage.contains("no channel")
                || normalizedMessage.contains("upstream")
                || normalizedMessage.contains("bad gateway")
                || normalizedMessage.contains("502")
                || normalizedMessage.contains("503")
                || normalizedMessage.contains("temporarily unavailable")
                || normalizedMessage.contains("input json is empty")
                || normalizedMessage.contains("no sources available")
                || normalizedMessage.contains("syntaxerror")
                // 该免费网关在渠道异常时偶发把有效密钥误报为 invalid token,实测重试可成功
                || normalizedMessage.contains("invalid token")
                || normalizedMessage.contains("无可用")
                || normalizedMessage.contains("当前分组")
                || normalizedMessage.contains("分组");
    }

    private boolean shouldRetryWithCompatPayload(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("html")
                || normalized.contains("not valid json")
                || normalized.contains("不是有效 json")
                || normalized.contains("unexpected character ('<'")
                || normalized.contains("unknown parameter")
                || normalized.contains("unsupported")
                || normalized.contains("not supported")
                || normalized.contains("response_format")
                || normalized.contains("upper limit")
                || normalized.contains("6mb")
                || shouldRetryWithMinimalPayload(normalized);
    }

    private boolean shouldRetryWithMinimalPayload(IllegalStateException ex) {
        String message = ex.getMessage();
        return message != null && shouldRetryWithMinimalPayload(message.toLowerCase(Locale.ROOT));
    }

    private boolean shouldRetryWithMinimalPayload(String normalizedMessage) {
        return normalizedMessage.contains("返回空响应")
                || normalizedMessage.contains("空响应体")
                || normalizedMessage.contains("empty response")
                || normalizedMessage.contains("input json is empty")
                || normalizedMessage.contains("no sources available")
                || normalizedMessage.contains("syntaxerror")
                || normalizedMessage.contains("syntax error");
    }

    private boolean shouldRetryWithoutUrlResponse(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("unknown parameter")
                || normalized.contains("unsupported")
                || normalized.contains("not supported")
                || normalized.contains("response_format");
    }

    private boolean shouldRetryWithInlineImageData(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("下载生成图片失败")
                || normalized.contains("下载模型结果超时")
                || normalized.contains("当前无法连通")
                || normalized.contains("header parser received no bytes")
                || normalized.contains("processing generated image failed")
                || normalized.contains("connection reset")
                || normalized.contains("prematurely reached end of stream")
                || normalized.contains("unexpected end of file");
    }

    private IllegalStateException explainOriginalAssetUnavailable(IllegalStateException fallbackEx,
                                                                  IllegalStateException originalEx) {
        String fallbackMessage = fallbackEx.getMessage() == null ? "" : fallbackEx.getMessage().toLowerCase(Locale.ROOT);
        if (fallbackMessage.contains("upper limit (6mb)")) {
            return new IllegalStateException(
                    "第三方提供商已生成完成，但返回的结果文件地址当前机器无法访问；尝试改用内联结果回传时又被供应商 6MB 响应上限拒绝，因此暂时无法取回可显示图片。",
                    originalEx
            );
        }
        return fallbackEx;
    }

    private boolean supportsQualityParameter(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String normalized = modelName.trim().toLowerCase(Locale.ROOT);
        return !normalized.startsWith("gpt-image");
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

    private String abbreviateBody(String body) {
        if (body == null || body.isBlank()) {
            return "空响应体";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 160) {
            return compact;
        }
        return compact.substring(0, 160) + "...";
    }

    private String buildHtmlEndpointError(String url, int statusCode, String body) {
        if (body != null && body.toLowerCase(Locale.ROOT).contains("<title>nl-api</title>")) {
            return "模型服务返回了 nl-api 管理页 HTML（HTTP " + statusCode + "），当前 Base URL 很可能填成了站点地址而不是 OpenAI 接口地址: " + url;
        }
        return "模型服务返回了 HTML 页面（HTTP " + statusCode + "），请确认 Base URL 指向 OpenAI 兼容接口地址而不是网站首页: " + url;
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
        } catch (Exception ignored) {
        }
        return body == null || body.isBlank() ? "图片生成接口返回错误" : body;
    }

    private List<RenderedMedia> extractMedia(JsonNode node, String requestedSize) {
        List<RenderedMedia> media = new ArrayList<>();
        JsonNode candidates = node.path("data");
        if (candidates.isArray() && candidates.size() > 0) {
            for (int i = 0; i < candidates.size(); i++) {
                JsonNode item = candidates.get(i);
                media.add(resolveCandidate(item, requestedSize, i));
            }
            return media;
        }

        JsonNode output = node.path("output");
        if (output.isArray() && output.size() > 0) {
            for (int i = 0; i < output.size(); i++) {
                JsonNode item = output.get(i);
                media.add(resolveCandidate(item, requestedSize, i));
            }
            return media;
        }

        if (node.hasNonNull("b64_json") || node.hasNonNull("url")) {
            media.add(resolveCandidate(node, requestedSize, 0));
            return media;
        }

        throw new IllegalStateException("图片生成接口未返回可用结果");
    }

    private RenderedMedia resolveCandidate(JsonNode node, String requestedSize, int index) {
        try {
            byte[] rawBytes;
            String mimeType = "image/png";
            if (node.hasNonNull("b64_json")) {
                rawBytes = Base64.getDecoder().decode(node.get("b64_json").asText());
                if (node.hasNonNull("mime_type")) {
                    mimeType = node.get("mime_type").asText(mimeType);
                }
            } else if (node.hasNonNull("url")) {
                String sourceUrl = node.get("url").asText();
                DownloadedMedia downloaded;
                try {
                    downloaded = downloadGeneratedImage(sourceUrl, mimeType);
                } catch (IllegalStateException ex) {
                    log.warn("供应商已返回生成结果 URL，但服务端下载失败，将先保存外链资产: {}", ex.getMessage());
                    return remoteGeneratedMedia(sourceUrl, fallbackRemoteMimeType(node, mimeType), index);
                }
                rawBytes = downloaded.bytes();
                mimeType = downloaded.mimeType();
            } else {
                throw new IllegalStateException("生成结果缺少图片数据");
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(rawBytes));
            if (image == null) {
                throw new IllegalStateException("无法解析生成图片");
            }

            int[] target = resolveTargetSize(requestedSize, image.getWidth(), image.getHeight());
            BufferedImage normalized = scaleAndCrop(image, target[0], target[1]);
            byte[] pngBytes = writeImage(normalized, "png");
            String fileName = buildFileName(index);
            return saveGeneratedMedia(pngBytes, "image/png", fileName);
        } catch (Exception ex) {
            if (ex instanceof java.net.http.HttpTimeoutException) {
                throw new IllegalStateException("处理生成图片失败: 下载模型结果超时，已等待 " + MEDIA_DOWNLOAD_READ_TIMEOUT.toSeconds() + " 秒", ex);
            }
            if (ex instanceof java.net.ConnectException) {
                throw new IllegalStateException("处理生成图片失败: 供应商结果文件地址当前无法连通", ex);
            }
            throw new IllegalStateException("处理生成图片失败: " + ex.getMessage(), ex);
        }
    }

    private String fallbackRemoteMimeType(JsonNode node, String fallbackMimeType) {
        if (node.hasNonNull("mime_type")) {
            String value = node.get("mime_type").asText();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallbackMimeType == null || fallbackMimeType.isBlank() ? "image/png" : fallbackMimeType;
    }

    private RenderedMedia remoteGeneratedMedia(String sourceUrl, String mimeType, int index) {
        com.example.aihub.common.security.SsrfGuard.validate(sourceUrl);
        String fileName = remoteFileName(sourceUrl, mimeType, index);
        return new RenderedMedia(fileName, sourceUrl, sourceUrl, mimeType, 0L, null);
    }

    private String remoteFileName(String sourceUrl, String mimeType, int index) {
        String extension = extensionFromMimeType(mimeType);
        try {
            String path = URI.create(sourceUrl).getPath();
            if (path != null && !path.isBlank()) {
                String name = Paths.get(path).getFileName().toString();
                if (!name.isBlank() && name.contains(".")) {
                    return name.replaceAll("[^a-zA-Z0-9._-]+", "_");
                }
            }
        } catch (Exception ignored) {
        }
        return "remote_generated_" + System.currentTimeMillis() + "_" + index + "." + extension;
    }

    private String extensionFromMimeType(String mimeType) {
        String lower = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg")) return "jpg";
        if (lower.contains("webp")) return "webp";
        if (lower.contains("gif")) return "gif";
        if (lower.contains("png")) return "png";
        return "png";
    }

    private DownloadedMedia downloadGeneratedImage(String sourceUrl, String fallbackMimeType) {
        com.example.aihub.common.security.SsrfGuard.validate(sourceUrl);
        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= MAX_DOWNLOAD_ATTEMPTS; attempt++) {
            try {
                return downloadGeneratedImageOnce(sourceUrl, fallbackMimeType);
            } catch (Exception ex) {
                lastError = ex instanceof IllegalStateException stateEx
                        ? stateEx
                        : new IllegalStateException("下载生成图片失败: " + ex.getMessage(), ex);
                if (attempt < MAX_DOWNLOAD_ATTEMPTS && shouldRetryDownloadException(ex)) {
                    log.warn("生成图片下载第 {}/{} 次失败,将重试: {}", attempt, MAX_DOWNLOAD_ATTEMPTS, ex.getMessage());
                    sleepBeforeRetry(attempt, ex);
                    continue;
                }
                throw lastError;
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("下载生成图片失败: 已重试多次仍未成功");
    }

    private DownloadedMedia downloadGeneratedImageOnce(String sourceUrl, String fallbackMimeType) throws Exception {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(sourceUrl);
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout((int) MEDIA_DOWNLOAD_CONNECT_TIMEOUT.toMillis());
            connection.setReadTimeout((int) MEDIA_DOWNLOAD_READ_TIMEOUT.toMillis());
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 OmniSpark-AI/1.0");
            if (uri.getScheme() != null && uri.getHost() != null) {
                connection.setRequestProperty("Referer", uri.getScheme() + "://" + uri.getHost() + "/");
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("下载生成图片失败: HTTP " + statusCode);
            }

            byte[] body;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                body = output.toByteArray();
            }
            if (body.length == 0) {
                throw new IllegalStateException("下载生成图片失败: 供应商返回空文件");
            }
            String mimeType = connection.getContentType() == null ? fallbackMimeType : connection.getContentType();
            return new DownloadedMedia(body, mimeType);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean shouldRetryDownloadException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("read timed out")
                || normalized.contains("connect timed out")
                || normalized.contains("connection reset")
                || normalized.contains("unexpected end of file")
                || normalized.contains("prematurely reached end of stream")
                || normalized.contains("header parser received no bytes")
                || normalized.contains("502")
                || normalized.contains("503")
                || normalized.contains("504")
                || normalized.contains("temporarily unavailable");
    }

    private int[] resolveTargetSize(String requestedSize, int fallbackWidth, int fallbackHeight) {
        if (requestedSize == null || requestedSize.isBlank() || !requestedSize.contains("x")) {
            return new int[]{fallbackWidth, fallbackHeight};
        }
        String[] parts = requestedSize.toLowerCase(Locale.ROOT).split("x");
        if (parts.length != 2) {
            return new int[]{fallbackWidth, fallbackHeight};
        }
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException ex) {
            return new int[]{fallbackWidth, fallbackHeight};
        }
    }

    private BufferedImage scaleAndCrop(BufferedImage source, int targetWidth, int targetHeight) {
        if (source.getWidth() == targetWidth && source.getHeight() == targetHeight) {
            return source;
        }
        double scale = Math.max((double) targetWidth / source.getWidth(), (double) targetHeight / source.getHeight());
        int scaledWidth = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int scaledHeight = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        g.dispose();

        int x = Math.max(0, (scaledWidth - targetWidth) / 2);
        int y = Math.max(0, (scaledHeight - targetHeight) / 2);
        return scaled.getSubimage(x, y, Math.min(targetWidth, scaledWidth), Math.min(targetHeight, scaledHeight));
    }

    private byte[] writeImage(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private String buildFileName(int index) {
        return "generated_" + System.currentTimeMillis() + "_" + index + ".png";
    }

    private RenderedMedia saveGeneratedMedia(byte[] bytes, String mimeType, String fileName) throws Exception {
        Path uploadDir = uploadStorageResolver.resolve("generated");
        Files.createDirectories(uploadDir);
        Path target = uploadDir.resolve(fileName);
        Files.write(target, bytes);
        String url = "/uploads/generated/" + fileName;
        return new RenderedMedia(fileName, url, url, mimeType, (long) bytes.length, bytes);
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
            throw new IllegalStateException("构建 multipart 请求失败", ex);
        }
    }

    public byte[] downloadReferenceBytes(String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.isBlank()) {
                return new byte[0];
            }
            if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
                com.example.aihub.common.security.SsrfGuard.validate(fileUrl);
                HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl))
                        .timeout(REFERENCE_DOWNLOAD_TIMEOUT)
                        .GET()
                        .build();
                HttpResponse<byte[]> response = newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("下载参考图失败");
                }
                return response.body();
            }
            Path local = uploadStorageResolver.resolveLocalUploadPath(fileUrl);
            if (local == null) {
                local = Paths.get(fileUrl);
            }
            return Files.readAllBytes(local);
        } catch (Exception ex) {
            throw new IllegalStateException("读取参考图失败: " + ex.getMessage(), ex);
        }
    }

    public String detectMimeType(String fileName, String fallback) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return fallback == null || fallback.isBlank() ? "application/octet-stream" : fallback;
    }

    @Data
    @AllArgsConstructor
    public static class ReferenceImage {
        private byte[] bytes;
        private String fileName;
        private String mimeType;
    }

    @Data
    @AllArgsConstructor
    public static class RenderedMedia {
        private String fileName;
        private String fileUrl;
        private String thumbUrl;
        private String mimeType;
        private Long fileSize;
        private byte[] bytes;
    }

    private record DownloadedMedia(byte[] bytes, String mimeType) {
    }
}
