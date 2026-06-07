package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.security.UploadAccessSignatureService;
import com.example.aihub.common.storage.UploadStorageResolver;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.ImageGenerateDTO;
import com.example.aihub.infrastructure.dto.PromptOptimizeDTO;
import com.example.aihub.infrastructure.dto.TaskQueryDTO;
import com.example.aihub.infrastructure.dto.VideoGenerateDTO;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.GenerationTask;
import com.example.aihub.infrastructure.entity.ModelProvider;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.mapper.GenerationTaskMapper;
import com.example.aihub.infrastructure.mapper.ModelProviderMapper;
import com.example.aihub.infrastructure.mapper.QuotaRecordMapper;
import com.example.aihub.infrastructure.vo.AssetVO;
import com.example.aihub.infrastructure.vo.GenerationTaskVO;
import com.example.aihub.infrastructure.service.NotificationService;
import com.example.aihub.infrastructure.service.WebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenerationService {
    private static final Duration MEDIA_GENERATION_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration MEDIA_DOWNLOAD_TIMEOUT = Duration.ofMinutes(5);
    private static final String DEFAULT_IMAGE_RESOLUTION = "1k";
    private static final String DEFAULT_IMAGE_QUALITY = "standard";
    private static final String DEFAULT_VIDEO_DURATION = "5s";
    private static final String DEFAULT_VIDEO_CAMERA_MOTION = "zoom_in";
    private static final Set<String> SUPPORTED_IMAGE_PROVIDER_TYPES = Set.of("image", "openai", "custom");
    private static final Set<String> SUPPORTED_VIDEO_PROVIDER_TYPES = Set.of("video", "openai", "custom");
    private static final Set<String> SUPPORTED_PROMPT_OPTIMIZER_PROVIDER_TYPES = Set.of("openai", "custom");
    private static final Set<String> SUPPORTED_IMAGE_RESOLUTIONS = Set.of("custom", "1k", "2k", "4k");
    private static final Set<String> SUPPORTED_IMAGE_QUALITIES = Set.of("standard", "high", "ultra");
    private static final Set<String> SUPPORTED_VIDEO_DURATIONS = Set.of("5s", "10s");
    private static final Set<String> SUPPORTED_VIDEO_CAMERA_MOTIONS = Set.of("zoom_in", "zoom_out", "pan_right", "tilt_down", "rotate_cw");
    private static final List<Map<String, String>> IMAGE_RESOLUTION_OPTIONS = List.of(
            option("自定义尺寸", "custom"),
            option("1K 标清细节", "1k"),
            option("2K 高清细节", "2k"),
            option("4K 超清细节", "4k")
    );
    private static final List<Map<String, String>> IMAGE_QUALITY_OPTIONS = List.of(
            option("标准质量 (Standard)", "standard"),
            option("高质量 (High)", "high"),
            option("极致质量 (Ultra)", "ultra")
    );
    private static final List<Map<String, String>> VIDEO_DURATION_OPTIONS = List.of(
            option("5秒 (标准版)", "5s"),
            option("10秒 (高清长片)", "10s")
    );
    private static final List<Map<String, String>> VIDEO_CAMERA_MOTION_OPTIONS = List.of(
            option("镜头向前平推 (Zoom In)", "zoom_in"),
            option("镜头向后拉远 (Zoom Out)", "zoom_out"),
            option("从左向右摇移 (Pan Right)", "pan_right"),
            option("俯仰向下扫描 (Tilt Down)", "tilt_down"),
            option("顺时针3D环绕 (Rotate CW)", "rotate_cw")
    );

    private final GenerationTaskMapper taskMapper;
    private final AssetMapper assetMapper;
    private final ModelProviderMapper providerMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final OpenAiImageClient imageClient;
    private final OpenAiTextClient textClient;
    private final NotificationService notificationService;
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;
    private final UploadAccessSignatureService uploadAccessSignatureService;
    private final UploadStorageResolver uploadStorageResolver;
    private final AssetService assetService;
    // 有界队列 + AbortPolicy：过载时拒绝而非无限堆积内存，由调用方捕获并提示用户稍后重试
    private final ThreadPoolExecutor generationExecutor = new ThreadPoolExecutor(
            4, 4,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(50),
            new ThreadPoolExecutor.AbortPolicy());
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    /** 静态系统配置：返回生成面板固定枚举，不代表实时业务数据。 */
    public Map<String, Object> meta() {
        return Map.of(
                "image", Map.of(
                        "allowedProviderTypes", SUPPORTED_IMAGE_PROVIDER_TYPES,
                        "resolutionOptions", IMAGE_RESOLUTION_OPTIONS,
                        "qualityOptions", IMAGE_QUALITY_OPTIONS,
                        "defaults", Map.of(
                                "resolution", DEFAULT_IMAGE_RESOLUTION,
                                "quality", DEFAULT_IMAGE_QUALITY
                        )
                ),
                "video", Map.of(
                        "allowedProviderTypes", SUPPORTED_VIDEO_PROVIDER_TYPES,
                        "durationOptions", VIDEO_DURATION_OPTIONS,
                        "cameraMotionOptions", VIDEO_CAMERA_MOTION_OPTIONS,
                        "defaults", Map.of(
                                "duration", DEFAULT_VIDEO_DURATION,
                                "cameraMotion", DEFAULT_VIDEO_CAMERA_MOTION
                        )
                )
        );
    }

    public List<GenerationTaskVO> list(TaskQueryDTO query) {
        LambdaQueryWrapper<GenerationTask> wrapper = buildTaskQueryWrapper(query);
        wrapper.last("LIMIT " + PagingUtil.clampLimit(query.getLimit() == null ? 0 : query.getLimit(), 100, 100));
        return taskMapper.selectList(wrapper).stream().map(this::toTaskVO).toList();
    }

    public PageResult<GenerationTaskVO> page(TaskQueryDTO query) {
        LambdaQueryWrapper<GenerationTask> wrapper = buildTaskQueryWrapper(query);
        Page<GenerationTask> result = taskMapper.selectPage(
                new Page<>(PagingUtil.normalizePage(query.getPage()), PagingUtil.clampPageSize(query.getPageSize(), 100)),
                wrapper
        );
        return new PageResult<>(result.getTotal(), result.getPages(), result.getRecords().stream().map(this::toTaskVO).toList());
    }

    public GenerationTaskVO getTask(Long id) {
        GenerationTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        projectAccessGuard.assertAccess(task.getProjectId());
        return toTaskVO(task);
    }

    public GenerationTaskVO generateInpaint(ImageGenerateDTO dto) {
        if (dto.getMaskAssetId() == null) {
            throw new BusinessException("局部重绘需要上传遮罩图（mask）");
        }
        if (dto.getReferenceAssetIds() == null || dto.getReferenceAssetIds().isEmpty()) {
            throw new BusinessException("局部重绘需要提供原始参考图");
        }
        projectAccessGuard.assertEditAccess(dto.getProjectId());
        dto.setOptions(normalizeImageOptions(dto.getOptions()));
        ModelProvider provider = requireProvider(dto.getProviderId(), dto.getProjectId(), "image");
        Long userId = SecurityUtil.loginUserId();
        String modelName = resolveModelName(dto.getModelName(), dto.getOptions(), provider.getModelName());
        GenerationTask task = createTask(dto.getProjectId(), provider.getId(), "image", dto.getPrompt(),
                dto.getNegativePrompt(), dto.getSize(), dto.getCount(), dto.getOptions(), dto.getReferenceAssetIds(), null, null, null, modelName);
        submitImageInpaintTask(task, userId, provider, modelName, dto);
        return toTaskVO(taskMapper.selectById(task.getId()));
    }

    public GenerationTaskVO generateImage(ImageGenerateDTO dto) {
        projectAccessGuard.assertEditAccess(dto.getProjectId());
        dto.setOptions(normalizeImageOptions(dto.getOptions()));
        ModelProvider provider = requireProvider(dto.getProviderId(), dto.getProjectId(), "image");
        Long userId = SecurityUtil.loginUserId();
        String modelName = resolveModelName(dto.getModelName(), dto.getOptions(), provider.getModelName());
        GenerationTask task = createTask(dto.getProjectId(), provider.getId(), "image", dto.getPrompt(),
                dto.getNegativePrompt(), dto.getSize(), dto.getCount(), dto.getOptions(), dto.getReferenceAssetIds(), null, null, null, modelName);
        submitImageTask(task, userId, provider, modelName, dto);
        return toTaskVO(taskMapper.selectById(task.getId()));
    }

    public GenerationTaskVO generateVideo(VideoGenerateDTO dto) {
        projectAccessGuard.assertEditAccess(dto.getProjectId());
        dto.setDuration(normalizeVideoDuration(dto.getDuration()));
        dto.setOptions(normalizeVideoOptions(dto.getOptions()));
        ModelProvider provider = requireProvider(dto.getProviderId(), dto.getProjectId(), "video");
        String modelName = resolveModelName(dto.getModelName(), dto.getOptions(), provider.getModelName());
        GenerationTask task = createTask(dto.getProjectId(), provider.getId(), "video", dto.getPrompt(),
                null, dto.getDuration(), null, dto.getOptions(), null, dto.getSourceAssetId(), dto.getDuration(), dto.getEndAssetId(), modelName);
        try {
            List<MediaRecord> mediaList = generateVideoMedia(provider, dto, modelName);
            return completeTask(task, null, mediaList, "video");
        } catch (Exception ex) {
            log.error("Video generation failed. taskId={}, providerId={}, modelName={}, prompt={}",
                    task.getId(), provider.getId(), modelName, dto.getPrompt(), ex);
            markTaskFailed(task.getId(), ex.getMessage());
            throw new BusinessException(ex.getMessage());
        }
    }

    public Map<String, Object> optimizeImagePrompt(PromptOptimizeDTO dto) {
        projectAccessGuard.assertEditAccess(dto.getProjectId());
        ModelProvider provider = resolvePromptOptimizerProvider(dto.getProjectId(), dto.getProviderId());
        String modelName = resolvePromptOptimizerModel(provider, dto.getModelName());
        if (modelName == null || modelName.isBlank()) {
            throw new BusinessException("未找到可用的提示词润色文本模型，请在模型配置中心为 OpenAI / Custom 提供商填写文本模型（例如 gpt-4o-mini）");
        }
        String optimizedPrompt = textClient.optimizeImagePrompt(
                provider.getBaseUrl(),
                provider.getApiKey(),
                modelName,
                dto.getPrompt()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt", optimizedPrompt);
        result.put("providerId", provider.getId());
        result.put("providerName", provider.getName());
        result.put("modelName", modelName);
        return result;
    }

    public GenerationTaskVO retry(Long id) {
        GenerationTask oldTask = taskMapper.selectById(id);
        if (oldTask == null) {
            throw new BusinessException("任务不存在");
        }
        projectAccessGuard.assertEditAccess(oldTask.getProjectId());
        try {
            Map<String, Object> request = objectMapper.readValue(oldTask.getRequestJson(), Map.class);
            if ("image".equals(oldTask.getTaskType())) {
                return generateImage(buildImageRetryDto(request));
            }
            if ("video".equals(oldTask.getTaskType())) {
                return generateVideo(buildVideoRetryDto(request));
            }
            throw new BusinessException("不支持的任务类型");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("重试失败: " + ex.getMessage());
        }
    }

    public void delete(Long id) {
        GenerationTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        projectAccessGuard.assertEditAccess(task.getProjectId());
        deleteTaskInternal(id);
    }

    public void adminDelete(Long id) {
        GenerationTask task = taskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        deleteTaskInternal(id);
    }

    @PreDestroy
    public void shutdownExecutor() {
        generationExecutor.shutdown();
    }

    private GenerationTask createTask(Long projectId,
                                       Long providerId,
                                       String taskType,
                                       String prompt,
                                       String negativePrompt,
                                       String size,
                                       Integer count,
                                       Map<String, Object> options,
                                       List<Long> referenceAssetIds,
                                       Long sourceAssetId,
                                       String duration,
                                       Long endAssetId,
                                       String modelName) {
        GenerationTask task = new GenerationTask();
        task.setProjectId(projectId);
        task.setProviderId(providerId);
        task.setTaskType(taskType);
        task.setPrompt(prompt);
        task.setNegativePrompt(negativePrompt);
        task.setStatus("running");
        task.setProgress(10);
        task.setProgressText("已受理，正在请求模型服务");
        task.setModelName(modelName);
        task.setRequestJson(cn.hutool.json.JSONUtil.toJsonStr(buildRequestPayload(
                taskType, projectId, providerId, prompt, modelName, negativePrompt, size, count, options, referenceAssetIds, sourceAssetId, duration, endAssetId)));
        taskMapper.insert(task);
        try {
            webhookService.trigger("task.started", task.getId(), taskType, task.getStatus(), task.getPrompt());
        } catch (Exception ignored) {}
        return task;
    }

    private void submitImageTask(GenerationTask task,
                                 Long userId,
                                 ModelProvider provider,
                                 String modelName,
                                 ImageGenerateDTO dto) {
        List<Long> referenceAssetIds = dto.getReferenceAssetIds() == null ? List.of() : List.copyOf(dto.getReferenceAssetIds());
        Map<String, Object> options = dto.getOptions() == null ? Collections.emptyMap() : new LinkedHashMap<>(dto.getOptions());
        submitGenerationJob(task.getId(), userId, () -> {
            try {
                updateTaskProgress(task.getId(), 20, "后台任务已启动，正在准备素材");
                List<OpenAiImageClient.ReferenceImage> references = loadReferenceImages(referenceAssetIds, task.getProjectId());
                int count = dto.getCount() == null || dto.getCount() < 1 ? 1 : dto.getCount();
                String requestSize = resolveRequestSize(dto.getSize(), options);
                String quality = resolveImageQuality(options);
                updateTaskProgress(task.getId(), 40, "素材准备完成，正在请求模型服务");
                List<OpenAiImageClient.RenderedMedia> mediaList = imageClient.generateImage(
                        provider.getBaseUrl(),
                        provider.getApiKey(),
                        modelName,
                        dto.getPrompt(),
                        count,
                        requestSize,
                        dto.getSize(),
                        quality,
                        references
                );
                updateTaskProgress(task.getId(), 80, "模型已返回结果，正在保存图片");
                completeTask(taskMapper.selectById(task.getId()), userId, dto.getCount(), mediaList, "image");
            } catch (Exception ex) {
                log.error("Async image generation failed. taskId={}, providerId={}, modelName={}, prompt={}",
                        task.getId(), provider.getId(), modelName, dto.getPrompt(), ex);
                markTaskFailed(task.getId(), userId, ex.getMessage());
            }
        });
    }

    private void submitImageInpaintTask(GenerationTask task,
                                        Long userId,
                                        ModelProvider provider,
                                        String modelName,
                                        ImageGenerateDTO dto) {
        List<Long> referenceAssetIds = dto.getReferenceAssetIds() == null ? List.of() : List.copyOf(dto.getReferenceAssetIds());
        Long maskAssetId = dto.getMaskAssetId();
        Map<String, Object> options = dto.getOptions() == null ? Collections.emptyMap() : new LinkedHashMap<>(dto.getOptions());
        submitGenerationJob(task.getId(), userId, () -> {
            try {
                updateTaskProgress(task.getId(), 20, "后台任务已启动，正在准备参考图与遮罩");
                List<OpenAiImageClient.ReferenceImage> references = loadReferenceImages(referenceAssetIds, task.getProjectId());
                if (references.isEmpty()) {
                    throw new BusinessException("无法加载原始参考图");
                }
                OpenAiImageClient.ReferenceImage maskImage = loadMaskImage(maskAssetId, task.getProjectId());
                int count = dto.getCount() == null || dto.getCount() < 1 ? 1 : dto.getCount();
                String requestSize = resolveRequestSize(dto.getSize(), options);
                updateTaskProgress(task.getId(), 40, "素材准备完成，正在请求模型服务");
                List<OpenAiImageClient.RenderedMedia> mediaList = imageClient.generateImageInpaint(
                        provider.getBaseUrl(),
                        provider.getApiKey(),
                        modelName,
                        dto.getPrompt(),
                        count,
                        requestSize,
                        dto.getSize(),
                        references.get(0),
                        maskImage
                );
                updateTaskProgress(task.getId(), 80, "模型已返回结果，正在保存图片");
                completeTask(taskMapper.selectById(task.getId()), userId, dto.getCount(), mediaList, "image");
            } catch (Exception ex) {
                log.error("Async inpaint generation failed. taskId={}, providerId={}, modelName={}, prompt={}",
                        task.getId(), provider.getId(), modelName, dto.getPrompt(), ex);
                markTaskFailed(task.getId(), userId, ex.getMessage());
            }
        });
    }

    /**
     * 提交后台生成任务到有界线程池。队列满时拒绝执行，将任务标记为失败并抛出友好异常，
     * 避免任务无限堆积导致内存压力。
     */
    private void submitGenerationJob(Long taskId, Long userId, Runnable job) {
        try {
            generationExecutor.submit(job);
        } catch (RejectedExecutionException ex) {
            log.warn("生成任务队列已满，拒绝新任务。taskId={}", taskId);
            markTaskFailed(taskId, userId, "当前生成任务过多，请稍后重试");
            throw new BusinessException("当前生成任务过多，请稍后重试");
        }
    }

    private Map<String, Object> buildRequestPayload(String taskType,
                                                    Long projectId,
                                                    Long providerId,
                                                    String prompt,
                                                    String modelName,
                                                    String negativePrompt,
                                                    String size,
                                                    Integer count,
                                                    Map<String, Object> options,
                                                    List<Long> referenceAssetIds,
                                                    Long sourceAssetId,
                                                    String duration,
                                                    Long endAssetId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskType", taskType);
        payload.put("projectId", projectId);
        payload.put("providerId", providerId);
        payload.put("prompt", prompt);
        payload.put("modelName", modelName);
        payload.put("negativePrompt", negativePrompt);
        payload.put("size", size);
        payload.put("count", count);
        payload.put("options", options);
        payload.put("referenceAssetIds", referenceAssetIds);
        payload.put("sourceAssetId", sourceAssetId);
        if ("video".equals(taskType)) {
            payload.put("duration", duration);
            payload.put("endAssetId", endAssetId);
        }
        return payload;
    }

    private GenerationTaskVO completeTask(GenerationTask task,
                                          Integer count,
                                          List<?> mediaList,
                                          String taskType) throws Exception {
        return completeTask(task, SecurityUtil.loginUserId(), count, mediaList, taskType);
    }

    private GenerationTaskVO completeTask(GenerationTask task,
                                          Long userId,
                                          Integer count,
                                          List<?> mediaList,
                                          String taskType) throws Exception {
        List<AssetVO> assetVOs = new ArrayList<>();
        Long resultAssetId = null;
        int index = 0;
        for (Object item : mediaList) {
            Asset asset;
            if (item instanceof OpenAiImageClient.RenderedMedia rendered) {
                asset = persistRenderedMedia(task, rendered, index++);
            } else if (item instanceof MediaRecord record) {
                asset = persistMedia(task, record, index++);
            } else {
                continue;
            }
            if (resultAssetId == null) {
                resultAssetId = asset.getId();
            }
            assetVOs.add(toSignedAssetVO(asset));
        }

        task.setStatus("success");
        task.setProgress(100);
        task.setProgressText("生成成功");
        task.setErrorMessage(null);
        task.setResultAssetId(resultAssetId);
        task.setResponseJson(cn.hutool.json.JSONUtil.toJsonStr(Map.of(
                "status", "success",
                "taskType", taskType,
                "assetIds", assetVOs.stream().map(AssetVO::getId).toList(),
                "assets", assetVOs
        )));
        taskMapper.updateById(task);
        quotaRecordMapper.insert(buildQuotaRecord(userId, task.getProjectId(), task.getId(), taskType, count));

        // 触发 Webhook
        try {
            webhookService.trigger("task.completed", task.getId(), taskType, "success", task.getPrompt());
        } catch (Exception ignored) {}

        // 发送完成通知
        try {
            String typeLabel = "image".equals(taskType) ? "生图" : "视频";
            notificationService.notify(
                    userId,
                    typeLabel + "任务完成 #" + task.getId(),
                    "您的" + typeLabel + "任务已成功生成，共 " + (mediaList != null ? mediaList.size() : 1) + " 个结果",
                    "success",
                    task.getId()
            );
        } catch (Exception ignored) {}

        return toTaskVO(task);
    }

    private Asset persistRenderedMedia(GenerationTask task, OpenAiImageClient.RenderedMedia media, int index) {
        Asset asset = new Asset();
        asset.setProjectId(task.getProjectId());
        asset.setTaskId(task.getId());
        asset.setAssetType(task.getTaskType());
        asset.setFileName(media.getFileName());
        asset.setFileUrl(media.getFileUrl());
        asset.setThumbUrl(media.getThumbUrl());
        asset.setMimeType(media.getMimeType());
        asset.setFileSize(media.getFileSize());
        asset.setPrompt(task.getPrompt());
        asset.setModelName(task.getModelName());
        asset.setFavorite(0);
        assetMapper.insert(asset);
        return asset;
    }

    private Asset persistMedia(GenerationTask task, MediaRecord media, int index) {
        Asset asset = new Asset();
        asset.setProjectId(task.getProjectId());
        asset.setTaskId(task.getId());
        asset.setAssetType(task.getTaskType());
        asset.setFileName(media.fileName());
        asset.setFileUrl(media.fileUrl());
        asset.setThumbUrl(media.thumbUrl());
        asset.setMimeType(media.mimeType());
        asset.setFileSize(media.fileSize());
        asset.setPrompt(task.getPrompt());
        asset.setModelName(task.getModelName());
        asset.setFavorite(0);
        assetMapper.insert(asset);
        return asset;
    }

    private void markTaskFailed(Long taskId, String message) {
        markTaskFailed(taskId, SecurityUtil.loginUserId(), message);
    }

    private void markTaskFailed(Long taskId, Long userId, String message) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus("failed");
        task.setProgress(0);
        task.setProgressText("生成失败");
        task.setErrorMessage(message);
        taskMapper.updateById(task);

        // 触发 Webhook
        try {
            webhookService.trigger("task.failed", task.getId(), task.getTaskType(), "failed", task.getPrompt());
        } catch (Exception ignored) {}

        try {
            String typeLabel = "image".equals(task.getTaskType()) ? "生图" : "视频";
            notificationService.notify(
                    userId,
                    typeLabel + "任务失败 #" + task.getId(),
                    "您的" + typeLabel + "任务执行失败: " + (message != null ? message.substring(0, Math.min(message.length(), 100)) : "未知错误"),
                    "error",
                    task.getId()
            );
        } catch (Exception ignored) {}
    }

    private ModelProvider requireProvider(Long providerId, Long projectId, String taskType) {
        ModelProvider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BusinessException("模型提供商不存在");
        }
        projectAccessGuard.assertAccess(provider.getProjectId());
        if (!provider.getProjectId().equals(projectId)) {
            throw new BusinessException("模型提供商不属于当前项目");
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            throw new BusinessException("当前模型提供商已禁用");
        }
        String providerType = provider.getType() == null ? "" : provider.getType().trim().toLowerCase(Locale.ROOT);
        Set<String> allowedTypes = "video".equals(taskType) ? SUPPORTED_VIDEO_PROVIDER_TYPES : SUPPORTED_IMAGE_PROVIDER_TYPES;
        if (!allowedTypes.contains(providerType)) {
            throw new BusinessException(("video".equals(taskType) ? "当前提供商不支持视频生成" : "当前提供商不支持图片生成"));
        }
        return provider;
    }

    private ModelProvider resolvePromptOptimizerProvider(Long projectId, Long preferredProviderId) {
        if (preferredProviderId != null) {
            ModelProvider preferred = providerMapper.selectById(preferredProviderId);
            if (preferred == null) {
                throw new BusinessException("模型提供商不存在");
            }
            projectAccessGuard.assertAccess(preferred.getProjectId());
            if (!preferred.getProjectId().equals(projectId)) {
                throw new BusinessException("模型提供商不属于当前项目");
            }
        }

        List<ModelProvider> candidates = providerMapper.selectList(
                new LambdaQueryWrapper<ModelProvider>()
                        .eq(ModelProvider::getProjectId, projectId)
                        .eq(ModelProvider::getEnabled, 1)
                        .orderByDesc(ModelProvider::getIsDefault)
                        .orderByDesc(ModelProvider::getId));

        return candidates.stream()
                .filter(provider -> promptOptimizerProviderScore(provider, preferredProviderId) > 0)
                .max(java.util.Comparator.comparingInt(provider -> promptOptimizerProviderScore(provider, preferredProviderId)))
                .orElseThrow(() -> new BusinessException("未找到可用的提示词润色模型，请先在模型配置中心配置启用中的 OpenAI / Custom 文本模型"));
    }

    private List<OpenAiImageClient.ReferenceImage> loadReferenceImages(List<Long> referenceAssetIds, Long projectId) {
        if (referenceAssetIds == null || referenceAssetIds.isEmpty()) {
            return List.of();
        }
        Asset asset = requireProjectAsset(referenceAssetIds.get(0), projectId, "参考图不存在");
        byte[] bytes = imageClient.downloadReferenceBytes(asset.getFileUrl());
        String mimeType = imageClient.detectMimeType(asset.getFileName(), asset.getMimeType());
        return List.of(new OpenAiImageClient.ReferenceImage(bytes, asset.getFileName(), mimeType));
    }

    private OpenAiImageClient.ReferenceImage loadMaskImage(Long maskAssetId, Long projectId) {
        if (maskAssetId == null) {
            throw new BusinessException("遮罩图 ID 不能为空");
        }
        Asset asset = requireProjectAsset(maskAssetId, projectId, "遮罩图不存在");
        byte[] bytes = imageClient.downloadReferenceBytes(asset.getFileUrl());
        String mimeType = imageClient.detectMimeType(asset.getFileName(), asset.getMimeType());
        return new OpenAiImageClient.ReferenceImage(bytes, asset.getFileName(), mimeType);
    }

    private ImageGenerateDTO buildImageRetryDto(Map<String, Object> request) {
        ImageGenerateDTO dto = new ImageGenerateDTO();
        dto.setProjectId(toLong(request.get("projectId")));
        dto.setProviderId(toLong(request.get("providerId")));
        dto.setPrompt(stringValue(request.get("prompt")));
        dto.setModelName(resolveModelName(stringValue(request.get("modelName")), null, null));
        dto.setNegativePrompt(stringValue(request.get("negativePrompt")));
        dto.setSize(stringValue(request.get("size")));
        dto.setCount(toInteger(request.get("count")));
        dto.setReferenceAssetIds(toLongList(request.get("referenceAssetIds")));
        Object options = request.get("options");
        if (options instanceof Map<?, ?> optionMap) {
            dto.setOptions((Map<String, Object>) optionMap);
            if (dto.getModelName() == null) {
                dto.setModelName(resolveModelName(null, dto.getOptions(), null));
            }
        } else {
            dto.setOptions(Collections.emptyMap());
        }
        return dto;
    }

    private VideoGenerateDTO buildVideoRetryDto(Map<String, Object> request) {
        VideoGenerateDTO dto = new VideoGenerateDTO();
        dto.setProjectId(toLong(request.get("projectId")));
        dto.setProviderId(toLong(request.get("providerId")));
        dto.setPrompt(stringValue(request.get("prompt")));
        dto.setModelName(resolveModelName(stringValue(request.get("modelName")), null, null));
        String duration = stringValue(request.get("duration"));
        dto.setDuration(duration != null ? duration : stringValue(request.get("size")));
        dto.setSourceAssetId(toLong(request.get("sourceAssetId")));
        dto.setEndAssetId(toLong(request.get("endAssetId")));
        Object options = request.get("options");
        if (options instanceof Map<?, ?> optionMap) {
            dto.setOptions((Map<String, Object>) optionMap);
            if (dto.getModelName() == null) {
                dto.setModelName(resolveModelName(null, dto.getOptions(), null));
            }
        } else {
            dto.setOptions(Collections.emptyMap());
        }
        return dto;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private List<Long> toLongList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : list) {
            Long parsed = toLong(item);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    private List<MediaRecord> generateVideoMedia(ModelProvider provider, VideoGenerateDTO dto, String modelName) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelName);
        payload.put("prompt", dto.getPrompt());
        payload.put("duration", dto.getDuration());
        payload.put("options", dto.getOptions());
        payload.put("sourceAssetId", dto.getSourceAssetId());
        if (dto.getSourceAssetId() != null) {
            Asset source = requireProjectAsset(dto.getSourceAssetId(), dto.getProjectId(), "源图片不存在");
            payload.put("sourceAssetUrl", source.getFileUrl());
        }
        // 尾帧 (end frame)
        payload.put("endAssetId", dto.getEndAssetId());
        if (dto.getEndAssetId() != null) {
            Asset endAsset = requireProjectAsset(dto.getEndAssetId(), dto.getProjectId(), "尾帧图片不存在");
            payload.put("endAssetUrl", endAsset.getFileUrl());
        }
        JsonNode response = postJson(resolveEndpoint(provider.getBaseUrl(), "/videos/generations"), provider.getApiKey(), payload);
        return extractMedia(response, modelName, "video");
    }

    private List<MediaRecord> extractMedia(JsonNode node, String modelName, String taskType) throws Exception {
        List<MediaRecord> results = new ArrayList<>();
        JsonNode data = node.path("data");
        if (data.isArray() && !data.isEmpty()) {
            for (int i = 0; i < data.size(); i++) {
                results.add(resolveMediaNode(data.get(i), modelName, taskType, i));
            }
            return results;
        }
        JsonNode output = node.path("output");
        if (output.isArray() && !output.isEmpty()) {
            for (int i = 0; i < output.size(); i++) {
                results.add(resolveMediaNode(output.get(i), modelName, taskType, i));
            }
            return results;
        }
        if (node.hasNonNull("url") || node.hasNonNull("b64_json")) {
            results.add(resolveMediaNode(node, modelName, taskType, 0));
            return results;
        }
        throw new BusinessException("生成接口未返回可用的媒体结果");
    }

    private MediaRecord resolveMediaNode(JsonNode node, String modelName, String taskType, int index) throws Exception {
        byte[] bytes;
        String mimeType = "video".equals(taskType) ? "video/mp4" : "image/png";
        if (node.hasNonNull("b64_json")) {
            bytes = Base64.getDecoder().decode(node.get("b64_json").asText());
            if (node.hasNonNull("mime_type")) {
                mimeType = node.get("mime_type").asText(mimeType);
            }
        } else if (node.hasNonNull("url")) {
            String mediaUrl = node.get("url").asText();
            com.example.aihub.common.security.SsrfGuard.validate(mediaUrl);
            HttpRequest request = HttpRequest.newBuilder(URI.create(mediaUrl))
                    .timeout(MEDIA_DOWNLOAD_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("下载生成结果失败");
            }
            bytes = response.body();
            mimeType = response.headers().firstValue("content-type").orElse(mimeType);
        } else {
            throw new BusinessException("生成结果缺少媒体数据");
        }
        return persistRawMedia(bytes, mimeType, modelName, taskType, index);
    }

    private MediaRecord persistRawMedia(byte[] bytes, String mimeType, String modelName, String taskType, int index) throws Exception {
        String extension = detectExtension(mimeType, taskType);
        String safeModel = modelName == null ? "model" : modelName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]+", "_");
        String fileName = safeModel + "_" + System.currentTimeMillis() + "_" + index + "." + extension;
        Path uploadDir = uploadStorageResolver.resolve("generated");
        Files.createDirectories(uploadDir);
        Path target = uploadDir.resolve(fileName);
        Files.write(target, bytes);
        String fileUrl = "/uploads/generated/" + fileName;
        return new MediaRecord(fileName, fileUrl, fileUrl, mimeType, (long) bytes.length);
    }

    private String detectExtension(String mimeType, String taskType) {
        if (mimeType == null) {
            return "video".equals(taskType) ? "mp4" : "png";
        }
        String lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.contains("jpeg")) return "jpg";
        if (lower.contains("png")) return "png";
        if (lower.contains("webp")) return "webp";
        if (lower.contains("gif")) return "gif";
        if (lower.contains("mp4")) return "mp4";
        if (lower.contains("quicktime")) return "mov";
        return "video".equals(taskType) ? "mp4" : "png";
    }

    private JsonNode postJson(String url, String apiKey, Map<String, Object> payload) throws Exception {
        com.example.aihub.common.security.SsrfGuard.validate(url);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(MEDIA_GENERATION_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new BusinessException("模型服务响应超时，已等待 " + MEDIA_GENERATION_TIMEOUT.toMinutes() + " 分钟");
        }
        String body = response.body();
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            if (looksLikeHtml(body, contentType)) {
                throw new BusinessException(buildHtmlEndpointError(url, response.statusCode(), body));
            }
            throw new BusinessException(extractErrorMessage(body));
        }
        if (looksLikeHtml(body, contentType)) {
            throw new BusinessException(buildHtmlEndpointError(url, response.statusCode(), body));
        }
        return objectMapper.readTree(body);
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
        return body == null || body.isBlank() ? "远程生成接口返回错误" : body;
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
        return "模型服务返回了 HTML 页面（HTTP " + statusCode + "），请确认 Base URL 指向接口而不是网站首页: " + url;
    }

    private String resolveEndpoint(String baseUrl, String suffix) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith(suffix)) {
            return trimmed;
        }
        return trimmed + suffix;
    }

    private String resolveRequestSize(String targetSize, Map<String, Object> options) {
        if (targetSize == null || targetSize.isBlank() || !targetSize.contains("x")) {
            return "1024x1024";
        }
        String[] parts = targetSize.toLowerCase(Locale.ROOT).split("x");
        if (parts.length != 2) {
            return "1024x1024";
        }
        try {
            int width = Integer.parseInt(parts[0]);
            int height = Integer.parseInt(parts[1]);
            if (Math.abs(width - height) <= 64) {
                return "1024x1024";
            }
            return width >= height ? "1536x1024" : "1024x1536";
        } catch (NumberFormatException ex) {
            return "1024x1024";
        }
    }

    private String resolveImageQuality(Map<String, Object> options) {
        if (options == null) {
            return "low";
        }
        Object quality = options.get("quality");
        if (quality == null) {
            return "low";
        }
        String value = quality.toString().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "high" -> "medium";
            case "ultra" -> "high";
            default -> "low";
        };
    }

    private String resolveModelName(String explicitModelName, Map<String, Object> options, String fallback) {
        if (explicitModelName != null && !explicitModelName.isBlank()) {
            return explicitModelName.trim();
        }
        if (options != null) {
            Object optionModelName = options.get("modelName");
            if (optionModelName != null) {
                String optionValue = optionModelName.toString().trim();
                if (!optionValue.isBlank()) {
                    return optionValue;
                }
            }
        }
        return fallback;
    }

    private String resolvePromptOptimizerModel(ModelProvider provider, String explicitModelName) {
        String explicit = normalizeText(explicitModelName);
        if (explicit != null) {
            return explicit;
        }
        String configModel = extractProviderConfigText(provider.getConfigJson(),
                "promptOptimizeModel", "chatModel", "textModel", "completionModel");
        if (configModel != null) {
            return configModel;
        }
        return normalizeText(provider.getModelName());
    }

    private String normalizeSizeLabel(String size) {
        if (size == null || size.isBlank()) {
            return "1024x1024";
        }
        return size;
    }

    private Map<String, Object> normalizeImageOptions(Map<String, Object> options) {
        Map<String, Object> normalized = options == null ? new LinkedHashMap<>() : new LinkedHashMap<>(options);
        String resolution = textValue(normalized.get("resolution"));
        if (resolution != null && !SUPPORTED_IMAGE_RESOLUTIONS.contains(resolution)) {
            throw new BusinessException("不支持的输出分辨率档位: " + resolution);
        }
        String quality = textValue(normalized.get("quality"));
        if (quality != null && !SUPPORTED_IMAGE_QUALITIES.contains(quality)) {
            throw new BusinessException("不支持的生成质量档位: " + quality);
        }
        normalized.put("resolution", resolution == null ? DEFAULT_IMAGE_RESOLUTION : resolution);
        normalized.put("quality", quality == null ? DEFAULT_IMAGE_QUALITY : quality);
        return normalized;
    }

    private Map<String, Object> normalizeVideoOptions(Map<String, Object> options) {
        Map<String, Object> normalized = options == null ? new LinkedHashMap<>() : new LinkedHashMap<>(options);
        String cameraMotion = textValue(normalized.get("cameraMotion"));
        if (cameraMotion != null && !SUPPORTED_VIDEO_CAMERA_MOTIONS.contains(cameraMotion)) {
            throw new BusinessException("不支持的镜头运动方向: " + cameraMotion);
        }
        normalized.put("cameraMotion", cameraMotion == null ? DEFAULT_VIDEO_CAMERA_MOTION : cameraMotion);
        return normalized;
    }

    private String normalizeVideoDuration(String duration) {
        String normalized = duration == null ? "" : duration.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return DEFAULT_VIDEO_DURATION;
        }
        if (!SUPPORTED_VIDEO_DURATIONS.contains(normalized)) {
            throw new BusinessException("不支持的视频时长: " + duration);
        }
        return normalized;
    }

    private String textValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim().toLowerCase(Locale.ROOT);
        return text.isBlank() ? null : text;
    }

    private int promptOptimizerProviderScore(ModelProvider provider, Long preferredProviderId) {
        String type = normalizeText(provider.getType());
        if (type == null) {
            return Integer.MIN_VALUE;
        }
        type = type.toLowerCase(Locale.ROOT);
        if (!SUPPORTED_PROMPT_OPTIMIZER_PROVIDER_TYPES.contains(type)) {
            return Integer.MIN_VALUE;
        }
        if (provider.getEnabled() != null && provider.getEnabled() == 0) {
            return Integer.MIN_VALUE;
        }
        if (isBlank(provider.getBaseUrl()) || isBlank(provider.getApiKey())) {
            return Integer.MIN_VALUE;
        }

        int score = "openai".equals(type) ? 40 : 30;
        if (preferredProviderId != null && preferredProviderId.equals(provider.getId())) {
            score += 35;
        }
        if (provider.getIsDefault() != null && provider.getIsDefault() == 1) {
            score += 15;
        }

        String modelName = resolvePromptOptimizerModel(provider, null);
        if (looksLikeTextModel(modelName)) {
            score += 80;
        } else if (looksLikeNonTextModel(modelName)) {
            score -= 60;
        } else if (!isBlank(modelName)) {
            score += 10;
        }
        return score;
    }

    private boolean looksLikeTextModel(String modelName) {
        if (isBlank(modelName)) {
            return false;
        }
        String value = modelName.trim().toLowerCase(Locale.ROOT);
        if (looksLikeNonTextModel(value)) {
            return false;
        }
        return value.contains("gpt")
                || value.contains("claude")
                || value.contains("qwen")
                || value.contains("glm")
                || value.contains("deepseek")
                || value.contains("gemini")
                || value.contains("llama")
                || value.contains("mistral")
                || value.contains("command")
                || value.contains("chat")
                || value.contains("instruct")
                || value.contains("assistant")
                || value.contains("sonnet")
                || value.contains("haiku")
                || value.contains("opus")
                || value.contains("turbo");
    }

    private boolean looksLikeNonTextModel(String modelName) {
        if (isBlank(modelName)) {
            return false;
        }
        String value = modelName.trim().toLowerCase(Locale.ROOT);
        return value.contains("dall-e")
                || value.contains("dalle")
                || value.contains("gpt-image")
                || value.contains("image-1")
                || value.contains("flux")
                || value.contains("sdxl")
                || value.contains("stable-diffusion")
                || value.contains("sora")
                || value.contains("runway")
                || value.contains("kling")
                || value.contains("tts")
                || value.contains("speech")
                || value.contains("whisper")
                || value.contains("transcribe")
                || value.contains("audio");
    }

    private String extractProviderConfigText(String configJson, String... keys) {
        if (isBlank(configJson) || keys == null || keys.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            if (!node.isObject()) {
                return null;
            }
            for (String key : keys) {
                if (node.hasNonNull(key)) {
                    String value = normalizeText(node.get(key).asText());
                    if (value != null) {
                        return value;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private void updateTaskProgress(Long taskId, int progress, String progressText) {
        GenerationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus("running");
        task.setProgress(progress);
        task.setProgressText(progressText);
        taskMapper.updateById(task);
    }

    private com.example.aihub.infrastructure.entity.QuotaRecord buildQuotaRecord(Long userId, Long projectId, Long taskId, String taskType, Integer amountOverride) {
        com.example.aihub.infrastructure.entity.QuotaRecord record = new com.example.aihub.infrastructure.entity.QuotaRecord();
        record.setUserId(userId);
        record.setProjectId(projectId);
        record.setTaskId(taskId);
        record.setQuotaType(taskType);
        record.setAmount(amountOverride != null ? amountOverride : ("video".equals(taskType) ? 5 : 1));
        record.setRemark("生成消耗");
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    private Asset requireProjectAsset(Long assetId, Long projectId, String missingMessage) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException(missingMessage);
        }
        projectAccessGuard.assertAccess(asset.getProjectId());
        if (!asset.getProjectId().equals(projectId)) {
            throw new BusinessException("资产不属于当前项目");
        }
        return asset;
    }

    private AssetVO toSignedAssetVO(Asset asset) {
        AssetVO vo = VoMapper.copy(asset, AssetVO.class);
        vo.setFileUrl(uploadAccessSignatureService.signProjectUrl(vo.getFileUrl(), asset.getProjectId()));
        vo.setThumbUrl(uploadAccessSignatureService.signProjectUrl(vo.getThumbUrl(), asset.getProjectId()));
        return vo;
    }

    private GenerationTaskVO toTaskVO(GenerationTask task) {
        return VoMapper.copy(task, GenerationTaskVO.class);
    }

    private LambdaQueryWrapper<GenerationTask> buildTaskQueryWrapper(TaskQueryDTO query) {
        LambdaQueryWrapper<GenerationTask> wrapper = new LambdaQueryWrapper<>();
        if (query.getProjectId() != null) {
            projectAccessGuard.assertAccess(query.getProjectId());
            wrapper.eq(GenerationTask::getProjectId, query.getProjectId());
        } else {
            List<Long> projectIds = projectAccessGuard.accessibleProjectIds();
            if (projectIds.isEmpty()) {
                wrapper.in(GenerationTask::getProjectId, List.of(-1L));
                return wrapper;
            }
            wrapper.in(GenerationTask::getProjectId, projectIds);
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(GenerationTask::getStatus, query.getStatus());
        }
        wrapper.orderByDesc(GenerationTask::getId);
        return wrapper;
    }

    private void deleteTaskInternal(Long taskId) {
        assetService.deleteByTaskId(taskId);
        quotaRecordMapper.update(null, new LambdaUpdateWrapper<com.example.aihub.infrastructure.entity.QuotaRecord>()
                .eq(com.example.aihub.infrastructure.entity.QuotaRecord::getTaskId, taskId)
                .set(com.example.aihub.infrastructure.entity.QuotaRecord::getTaskId, null));
        taskMapper.deleteById(taskId);
    }

    private record MediaRecord(String fileName, String fileUrl, String thumbUrl, String mimeType, Long fileSize) {
    }

    private static Map<String, String> option(String label, String value) {
        return Map.of("label", label, "value", value);
    }
}
