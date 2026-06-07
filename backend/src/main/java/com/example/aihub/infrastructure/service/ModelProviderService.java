package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.security.ModelApiKeyEncryptor;
import com.example.aihub.common.security.SensitiveValueMasker;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.ModelProviderSaveDTO;
import com.example.aihub.infrastructure.dto.ModelProviderUpdateDTO;
import com.example.aihub.infrastructure.entity.ModelProvider;
import com.example.aihub.infrastructure.mapper.ModelProviderMapper;
import com.example.aihub.infrastructure.vo.ModelProviderVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ModelProviderService {
    private static final String DEFAULT_PROVIDER_TYPE = "image";
    private static final String DEFAULT_AUDIO_RESPONSE_FORMAT = "mp3";
    private static final Set<String> SUPPORTED_PROVIDER_TYPES = Set.of("image", "video", "audio", "openai", "custom");
    private static final Set<String> SUPPORTED_AUDIO_RESPONSE_FORMATS = Set.of("mp3", "wav", "ogg", "flac", "aac", "pcm", "opus");
    private static final List<Map<String, String>> PROVIDER_TYPE_OPTIONS = List.of(
            providerTypeOption("图像生成模型 (Image)", "image", "生图", "success"),
            providerTypeOption("视频生成模型 (Video)", "video", "生视频", "warning"),
            providerTypeOption("语音配音模型 (Audio / TTS)", "audio", "配音", "info"),
            providerTypeOption("OpenAI 兼容接口 (OpenAI)", "openai", "OpenAI", "info"),
            providerTypeOption("自定义复杂接口 (Custom)", "custom", "自定义", "default")
    );
    private static final List<Map<String, String>> AUDIO_RESPONSE_FORMAT_OPTIONS = List.of(
            option("MP3", "mp3"),
            option("WAV", "wav"),
            option("OGG", "ogg"),
            option("FLAC", "flac"),
            option("AAC", "aac"),
            option("PCM", "pcm"),
            option("Opus", "opus")
    );

    private final ModelProviderMapper providerMapper;
    private final ObjectMapper objectMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;
    private final ModelApiKeyEncryptor apiKeyEncryptor;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /** 静态系统配置：返回前端表单所需的固定选项，不读取业务表。 */
    public Map<String, Object> meta() {
        return Map.of(
                "providerTypes", PROVIDER_TYPE_OPTIONS,
                "audioResponseFormats", AUDIO_RESPONSE_FORMAT_OPTIONS,
                "defaults", Map.of(
                        "providerType", DEFAULT_PROVIDER_TYPE,
                        "audioResponseFormat", DEFAULT_AUDIO_RESPONSE_FORMAT
                )
        );
    }

    public List<ModelProviderVO> list(Long projectId, int limit) {
        LambdaQueryWrapper<ModelProvider> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectAccessGuard.assertAccess(projectId);
            wrapper.eq(ModelProvider::getProjectId, projectId);
        } else {
            // 未指定项目时，仅返回当前用户可访问项目下的提供商，避免泄露他人配置
            List<Long> accessibleIds = projectAccessGuard.accessibleProjectIds();
            if (accessibleIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(ModelProvider::getProjectId, accessibleIds);
        }
        wrapper.orderByDesc(ModelProvider::getId);
        wrapper.last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100));
        return providerMapper.selectList(wrapper)
                .stream()
                .map(this::toMaskedVO)
                .toList();
    }

    public PageResult<ModelProviderVO> page(Long projectId, long page, long pageSize) {
        LambdaQueryWrapper<ModelProvider> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectAccessGuard.assertAccess(projectId);
            wrapper.eq(ModelProvider::getProjectId, projectId);
        } else {
            List<Long> accessibleIds = projectAccessGuard.accessibleProjectIds();
            if (accessibleIds.isEmpty()) {
                return new PageResult<>(0, 0, List.of());
            }
            wrapper.in(ModelProvider::getProjectId, accessibleIds);
        }
        wrapper.orderByDesc(ModelProvider::getId);
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        Page<ModelProvider> result = providerMapper.selectPage(new Page<>(safePage, safePageSize), wrapper);
        return new PageResult<>(result.getTotal(), result.getPages(), result.getRecords().stream().map(this::toMaskedVO).toList());
    }

    /** 将 apiKey 脱敏后再转 VO，避免明文密钥下发到前端。 */
    private ModelProviderVO toMaskedVO(ModelProvider provider) {
        ModelProviderVO vo = VoMapper.copy(provider, ModelProviderVO.class);
        vo.setApiKey(maskApiKey(apiKeyEncryptor.decrypt(provider.getApiKey())));
        return vo;
    }

    static String maskApiKey(String apiKey) {
        return SensitiveValueMasker.maskKeepingEdges(apiKey, 4, 4);
    }

    /** 判断前端回传的值是否为掩码占位（未修改原始密钥）。 */
    static boolean isMaskedValue(String value) {
        return SensitiveValueMasker.looksMasked(value);
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelProviderVO create(ModelProviderSaveDTO dto) {
        ModelProvider provider = toEntity(dto);
        projectAccessGuard.assertEditAccess(provider.getProjectId());
        validateProviderBeforeSave(provider);
        provider.setApiKey(apiKeyEncryptor.encrypt(provider.getApiKey()));
        providerMapper.insert(provider);
        applyDefaultRule(provider);
        return toMaskedVO(provider);
    }

    @Transactional(rollbackFor = Exception.class)
    public ModelProviderVO update(Long id, ModelProviderUpdateDTO dto) {
        ModelProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException("模型提供商不存在");
        }
        projectAccessGuard.assertEditAccess(provider.getProjectId());
        boolean apiKeyChanged = dto.getApiKey() != null && !isMaskedValue(dto.getApiKey());
        if (apiKeyChanged) {
            provider.setApiKey(dto.getApiKey());
        }
        if (dto.getProjectId() != null) {
            projectAccessGuard.assertEditAccess(dto.getProjectId());
            provider.setProjectId(dto.getProjectId());
        }
        if (dto.getName() != null) {
            provider.setName(dto.getName());
        }
        if (dto.getType() != null) {
            provider.setType(dto.getType());
        }
        if (dto.getBaseUrl() != null) {
            provider.setBaseUrl(dto.getBaseUrl());
        }
        if (dto.getModelName() != null) {
            provider.setModelName(dto.getModelName());
        }
        if (dto.getEnabled() != null) {
            provider.setEnabled(dto.getEnabled() ? 1 : 0);
        }
        if (dto.getIsDefault() != null) {
            provider.setIsDefault(dto.getIsDefault() ? 1 : 0);
        }
        if (dto.getConfigJson() != null) {
            provider.setConfigJson(dto.getConfigJson());
        }
        validateProviderBeforeSave(provider);
        if (apiKeyChanged) {
            provider.setApiKey(apiKeyEncryptor.encrypt(provider.getApiKey()));
        }
        providerMapper.updateById(provider);
        applyDefaultRule(provider);
        return toMaskedVO(provider);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ModelProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException("模型提供商不存在");
        }
        projectAccessGuard.assertEditAccess(provider.getProjectId());
        providerMapper.deleteById(id);
    }

    public String testConnection(Long id) {
        ModelProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException("模型提供商不存在");
        }
        projectAccessGuard.assertEditAccess(provider.getProjectId());
        String rawKey = apiKeyEncryptor.decrypt(provider.getApiKey());
        if (isBlank(provider.getBaseUrl()) || isBlank(rawKey)) {
            throw new BusinessException("API Key 或 Base URL 不能为空");
        }
        try {
            String endpoint = resolveEndpoint(provider.getBaseUrl(), "/models");
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + rawKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
            return "连接成功";
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("连接测试失败: " + ex.getMessage());
        }
    }

    private ModelProvider toEntity(ModelProviderSaveDTO dto) {
        ModelProvider provider = new ModelProvider();
        provider.setProjectId(dto.getProjectId());
        provider.setName(normalizeText(dto.getName()));
        provider.setType(normalizeText(dto.getType()));
        provider.setBaseUrl(normalizeBaseUrl(dto.getBaseUrl()));
        provider.setApiKey(normalizeText(dto.getApiKey()));
        provider.setModelName(normalizeText(dto.getModelName()));
        provider.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        provider.setIsDefault(Boolean.TRUE.equals(dto.getIsDefault()) ? 1 : 0);
        provider.setConfigJson(dto.getConfigJson());
        return provider;
    }

    private void validateProviderBeforeSave(ModelProvider provider) {
        provider.setName(normalizeText(provider.getName()));
        provider.setType(normalizeProviderType(provider.getType()));
        provider.setBaseUrl(normalizeBaseUrl(provider.getBaseUrl()));
        provider.setApiKey(normalizeText(provider.getApiKey()));
        provider.setModelName(normalizeText(provider.getModelName()));
        if (!SUPPORTED_PROVIDER_TYPES.contains(provider.getType())) {
            throw new BusinessException("不支持的模型提供商类型: " + provider.getType());
        }
        if (isBlank(provider.getBaseUrl()) || isBlank(provider.getApiKey()) || isBlank(provider.getModelName())) {
            throw new BusinessException("API Base URL、API Key 和模型名称不能为空");
        }
        validateProviderConfig(provider);
        validateNoHtmlEndpoint(provider);
    }

    private void applyDefaultRule(ModelProvider provider) {
        if (provider.getIsDefault() == null || provider.getIsDefault() != 1) {
            return;
        }
        providerMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ModelProvider>()
                .eq(ModelProvider::getProjectId, provider.getProjectId())
                .eq(ModelProvider::getType, provider.getType())
                .ne(ModelProvider::getId, provider.getId())
                .set(ModelProvider::getIsDefault, 0));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeProviderType(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = normalizeText(value);
        if (normalized == null || normalized.isBlank()) {
            return normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void validateProviderConfig(ModelProvider provider) {
        if (provider.getConfigJson() == null || provider.getConfigJson().isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(provider.getConfigJson());
            if (!node.isObject()) {
                throw new BusinessException("模型提供商配置必须是 JSON 对象");
            }
            if ("audio".equals(provider.getType())) {
                String responseFormat = textOrNull(node, "responseFormat");
                if (responseFormat != null && !SUPPORTED_AUDIO_RESPONSE_FORMATS.contains(responseFormat.toLowerCase(Locale.ROOT))) {
                    throw new BusinessException("不支持的语音输出格式: " + responseFormat);
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("模型提供商配置 JSON 非法");
        }
    }

    private void validateNoHtmlEndpoint(ModelProvider provider) {
        try {
            String endpoint = resolveEndpoint(provider.getBaseUrl(), "/models");
            String validateKey = apiKeyEncryptor.decrypt(provider.getApiKey());
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + validateKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (looksLikeHtml(body, contentType)) {
                throw new BusinessException(buildHtmlEndpointError(endpoint, response.statusCode(), body));
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            // Ignore network/protocol issues here; save-time validation only blocks obvious site HTML endpoints.
        }
    }

    private String resolveEndpoint(String baseUrl, String suffix) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String endpoint = trimmed.endsWith(suffix) ? trimmed : trimmed + suffix;
        // 发起请求前做 SSRF 校验，禁止指向内网 / 云元数据等保留地址
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
        return body == null || body.isBlank() ? "模型服务返回错误" : body;
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
            return "连接测试命中了 nl-api 管理页 HTML（HTTP " + statusCode + "），当前 Base URL 很可能填成了站点地址而不是 OpenAI 接口地址: " + url;
        }
        return "连接测试返回了 HTML 页面（HTTP " + statusCode + "），请确认 Base URL 指向接口而不是网站首页: " + url;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        String value = node.get(fieldName).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> option(String label, String value) {
        return Map.of("label", label, "value", value);
    }

    private static Map<String, String> providerTypeOption(String label, String value, String shortLabel, String tagType) {
        return Map.of(
                "label", label,
                "value", value,
                "shortLabel", shortLabel,
                "tagType", tagType
        );
    }
}
