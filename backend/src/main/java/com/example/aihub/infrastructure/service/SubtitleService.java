package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.security.UploadAccessSignatureService;
import com.example.aihub.common.storage.UploadStorageResolver;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.SubtitleGenerateDTO;
import com.example.aihub.infrastructure.dto.SubtitleUpdateDTO;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.ModelProvider;
import com.example.aihub.infrastructure.entity.Subtitle;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.mapper.ModelProviderMapper;
import com.example.aihub.infrastructure.mapper.SubtitleMapper;
import com.example.aihub.infrastructure.vo.SubtitleVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubtitleService {
    private final SubtitleMapper subtitleMapper;
    private final AssetMapper assetMapper;
    private final ModelProviderMapper modelProviderMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;
    private final UploadAccessSignatureService uploadAccessSignatureService;
    private final UploadStorageResolver uploadStorageResolver;
    private final OpenAiSpeechClient speechClient;
    private final OpenAiTranscriptionClient transcriptionClient;
    private final ObjectMapper objectMapper;

    @Value("${app.media.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    public List<SubtitleVO> listByAsset(Long assetId, int limit) {
        requireAccessibleAsset(assetId);
        return subtitleMapper.selectList(
                new LambdaQueryWrapper<Subtitle>()
                        .eq(Subtitle::getAssetId, assetId)
                        .orderByDesc(Subtitle::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)))
                .stream().map(this::toVO).toList();
    }

    public PageResult<SubtitleVO> pageByAsset(Long assetId, long page, long pageSize) {
        requireAccessibleAsset(assetId);
        Page<Subtitle> result = subtitleMapper.selectPage(
                new Page<>(PagingUtil.normalizePage(page), PagingUtil.clampPageSize(pageSize, 100)),
                new LambdaQueryWrapper<Subtitle>()
                        .eq(Subtitle::getAssetId, assetId)
                        .orderByDesc(Subtitle::getId)
        );
        return new PageResult<>(result.getTotal(), result.getPages(), result.getRecords().stream().map(this::toVO).toList());
    }

    public SubtitleVO get(Long id) {
        Subtitle sub = subtitleMapper.selectById(id);
        if (sub == null) throw new BusinessException("字幕不存在");
        projectAccessGuard.assertAccess(sub.getProjectId());
        return toVO(sub);
    }

    @Transactional(rollbackFor = Exception.class)
    public SubtitleVO generate(SubtitleGenerateDTO dto) {
        Asset asset = requireAccessibleAsset(dto.getAssetId());
        if (!asset.getProjectId().equals(dto.getProjectId())) {
            throw new BusinessException("字幕所属项目与资产不一致");
        }
        if (!"video".equalsIgnoreCase(asset.getAssetType())) {
            throw new BusinessException("当前仅支持为视频资产生成字幕");
        }

        List<Subtitle> existingSubtitles = subtitleMapper.selectList(
                new LambdaQueryWrapper<Subtitle>().eq(Subtitle::getAssetId, dto.getAssetId()));
        existingSubtitles.forEach(item -> deleteVoiceFile(item.getVoiceUrl()));
        subtitleMapper.delete(new LambdaQueryWrapper<Subtitle>().eq(Subtitle::getAssetId, dto.getAssetId()));

        String srt = transcribeAssetToSrt(asset, dto.getLanguage(), dto.getPrompt());
        Subtitle sub = new Subtitle();
        sub.setAssetId(dto.getAssetId());
        sub.setProjectId(asset.getProjectId());
        sub.setLanguage(dto.getLanguage() != null ? dto.getLanguage() : "zh");
        sub.setSrtContent(srt);
        sub.setStatus(1);
        subtitleMapper.insert(sub);
        return toVO(sub);
    }

    @Transactional(rollbackFor = Exception.class)
    public SubtitleVO update(SubtitleUpdateDTO dto) {
        Subtitle sub = subtitleMapper.selectById(dto.getId());
        if (sub == null) throw new BusinessException("字幕不存在");
        projectAccessGuard.assertAccess(sub.getProjectId());
        sub.setSrtContent(dto.getSrtContent());
        if (dto.getLanguage() != null) sub.setLanguage(dto.getLanguage());
        subtitleMapper.updateById(sub);
        return toVO(sub);
    }

    /**
     * 生成配音 — 调用 TTS API 将字幕文本转为音频
     */
    @Transactional(rollbackFor = Exception.class)
    public SubtitleVO generateVoice(Long subtitleId) {
        Subtitle sub = subtitleMapper.selectById(subtitleId);
        if (sub == null) throw new BusinessException("字幕不存在");
        projectAccessGuard.assertAccess(sub.getProjectId());

        // 从 SRT 中提取纯文本
        String plainText = extractPlainTextFromSrt(sub.getSrtContent());
        if (plainText.isBlank()) {
            throw new BusinessException("字幕内容为空，无法生成配音");
        }

        try {
            ModelProvider provider = resolveVoiceProvider(sub.getProjectId());
            OpenAiSpeechClient.SpeechOptions speechOptions = resolveSpeechOptions(provider, sub.getLanguage());
            OpenAiSpeechClient.SynthesizedAudio audio = speechClient.synthesize(
                    provider.getBaseUrl(),
                    provider.getApiKey(),
                    resolveSpeechModel(provider),
                    plainText,
                    speechOptions
            );

            Path ttsDir = uploadStorageResolver.resolve("tts");
            Files.createDirectories(ttsDir);
            String fileName = "tts_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "." + audio.getExtension();
            Path target = ttsDir.resolve(fileName);
            Files.write(target, audio.getBytes());

            deleteVoiceFile(sub.getVoiceUrl());
            String voiceUrl = "/uploads/tts/" + fileName;
            sub.setVoiceUrl(voiceUrl);
            subtitleMapper.updateById(sub);
            return toVO(sub);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("配音生成失败: " + ex.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Subtitle sub = subtitleMapper.selectById(id);
        if (sub == null) {
            throw new BusinessException("字幕不存在");
        }
        projectAccessGuard.assertAccess(sub.getProjectId());
        deleteVoiceFile(sub.getVoiceUrl());
        subtitleMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByProjectId(Long projectId) {
        if (projectId == null || projectId <= 0) {
            return;
        }
        List<Subtitle> subtitles = subtitleMapper.selectList(
                new LambdaQueryWrapper<Subtitle>().eq(Subtitle::getProjectId, projectId));
        subtitles.forEach(sub -> deleteVoiceFile(sub.getVoiceUrl()));
        subtitleMapper.delete(new LambdaQueryWrapper<Subtitle>().eq(Subtitle::getProjectId, projectId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByAssetIds(List<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return;
        }
        List<Subtitle> subtitles = subtitleMapper.selectList(
                new LambdaQueryWrapper<Subtitle>().in(Subtitle::getAssetId, assetIds));
        subtitles.forEach(sub -> deleteVoiceFile(sub.getVoiceUrl()));
        subtitleMapper.delete(new LambdaQueryWrapper<Subtitle>().in(Subtitle::getAssetId, assetIds));
    }

    // ===== 内部方法 =====

    private String transcribeAssetToSrt(Asset asset, String language, String prompt) {
        ModelProvider provider = resolveTranscriptionProvider(asset.getProjectId());
        String modelName = resolveTranscriptionModel(provider);
        OpenAiTranscriptionClient.MediaFile mediaFile = extractAudioForTranscription(asset);
        String srt = transcriptionClient.transcribe(
                provider.getBaseUrl(),
                provider.getApiKey(),
                modelName,
                language,
                prompt,
                mediaFile
        );
        if (srt == null || srt.isBlank()) {
            throw new BusinessException("转写结果为空，无法生成字幕");
        }
        return srt;
    }

    private OpenAiTranscriptionClient.MediaFile extractAudioForTranscription(Asset asset) {
        Path sourcePath = uploadStorageResolver.resolveLocalUploadPath(asset.getFileUrl());
        if (sourcePath == null || !Files.exists(sourcePath)) {
            throw new BusinessException("找不到视频源文件，无法执行真实字幕转写");
        }
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("subtitle-transcription-");
            String safeBaseName = buildSafeBaseName(asset.getFileName());
            Path audioPath = tempDir.resolve(safeBaseName + ".mp3");
            Path ffmpegLogPath = tempDir.resolve("ffmpeg.log");
            ProcessBuilder processBuilder = new ProcessBuilder(
                    resolveFfmpegCommand(),
                    "-y",
                    "-i", sourcePath.toString(),
                    "-vn",
                    "-ac", "1",
                    "-ar", "16000",
                    "-codec:a", "libmp3lame",
                    "-b:a", "128k",
                    audioPath.toString()
            );
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ffmpegLogPath.toFile());
            Process process = processBuilder.start();
            boolean finished = process.waitFor(Duration.ofMinutes(3).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException("FFmpeg 提取音频超时");
            }
            String output = readTextFileQuietly(ffmpegLogPath);
            if (process.exitValue() != 0 || !Files.exists(audioPath) || Files.size(audioPath) == 0) {
                throw new BusinessException("FFmpeg 提取音频失败: " + abbreviate(output));
            }
            byte[] audioBytes = Files.readAllBytes(audioPath);
            return new OpenAiTranscriptionClient.MediaFile(audioBytes, safeBaseName + ".mp3", "audio/mpeg");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("提取视频音轨失败: " + ex.getMessage());
        } finally {
            deleteDirectoryQuietly(tempDir);
        }
    }

    private String extractPlainTextFromSrt(String srt) {
        if (srt == null) return "";
        StringBuilder text = new StringBuilder();
        for (String line : srt.split("\n")) {
            // 跳过序号行和时间轴行
            if (line.matches("^\\d+$")) continue;
            if (line.contains("-->")) continue;
            if (line.isBlank()) continue;
            text.append(line.trim()).append(" ");
        }
        return text.toString().trim();
    }

    private ModelProvider resolveVoiceProvider(Long projectId) {
        List<ModelProvider> providers = modelProviderMapper.selectList(
                new LambdaQueryWrapper<ModelProvider>()
                        .eq(ModelProvider::getProjectId, projectId)
                        .eq(ModelProvider::getEnabled, 1)
                        .orderByDesc(ModelProvider::getIsDefault)
                        .orderByDesc(ModelProvider::getId));
        return providers.stream()
                .filter(provider -> voiceProviderScore(provider) > 0)
                .max(Comparator.comparingInt(this::voiceProviderScore))
                .orElseThrow(() -> new BusinessException("未找到可用的语音模型提供商，请先在模型配置中心添加并启用一个“语音/TTS”提供商"));
    }

    private ModelProvider resolveTranscriptionProvider(Long projectId) {
        List<ModelProvider> providers = modelProviderMapper.selectList(
                new LambdaQueryWrapper<ModelProvider>()
                        .eq(ModelProvider::getProjectId, projectId)
                        .eq(ModelProvider::getEnabled, 1)
                        .orderByDesc(ModelProvider::getIsDefault)
                        .orderByDesc(ModelProvider::getId));
        return providers.stream()
                .filter(provider -> transcriptionProviderScore(provider) > 0)
                .max(Comparator.comparingInt(this::transcriptionProviderScore))
                .orElseThrow(() -> new BusinessException("未找到可用的字幕转写模型，请在模型配置中心为语音提供商补充转写模型（例如 whisper-1 / gpt-4o-mini-transcribe）"));
    }

    private int voiceProviderScore(ModelProvider provider) {
        int score = 0;
        String type = provider.getType() == null ? "" : provider.getType().trim().toLowerCase(Locale.ROOT);
        if ("audio".equals(type)) {
            score += 40;
        } else if ("openai".equals(type)) {
            score += 20;
        } else if ("custom".equals(type)) {
            score += 10;
        }
        if (provider.getIsDefault() != null && provider.getIsDefault() == 1) {
            score += 15;
        }
        if (looksLikeSpeechModel(provider.getModelName())) {
            score += 80;
        }
        if (configLooksLikeSpeech(provider.getConfigJson())) {
            score += 40;
        }
        return score;
    }

    private int transcriptionProviderScore(ModelProvider provider) {
        int score = 0;
        String type = provider.getType() == null ? "" : provider.getType().trim().toLowerCase(Locale.ROOT);
        if ("audio".equals(type)) {
            score += 40;
        } else if ("openai".equals(type)) {
            score += 20;
        } else if ("custom".equals(type)) {
            score += 10;
        }
        if (provider.getIsDefault() != null && provider.getIsDefault() == 1) {
            score += 15;
        }
        String transcriptionModel = extractProviderConfigText(provider, "transcriptionModel");
        if (looksLikeTranscriptionModel(transcriptionModel)) {
            score += 80;
        } else if (looksLikeTranscriptionModel(provider.getModelName())) {
            score += 60;
        }
        return score;
    }

    private boolean looksLikeSpeechModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String value = modelName.trim().toLowerCase(Locale.ROOT);
        return value.contains("tts")
                || value.contains("speech")
                || value.contains("voice")
                || value.contains("audio");
    }

    private boolean looksLikeTranscriptionModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return false;
        }
        String value = modelName.trim().toLowerCase(Locale.ROOT);
        return value.contains("transcribe")
                || value.contains("whisper")
                || value.contains("stt")
                || value.contains("speech-to-text");
    }

    private boolean configLooksLikeSpeech(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(configJson);
            return node.hasNonNull("voice")
                    || node.hasNonNull("responseFormat")
                    || node.hasNonNull("speed")
                    || node.hasNonNull("instructions");
        } catch (Exception ignored) {
            return false;
        }
    }

    private OpenAiSpeechClient.SpeechOptions resolveSpeechOptions(ModelProvider provider, String language) {
        String voice = null;
        String responseFormat = "mp3";
        Double speed = null;
        String instructions = null;
        if (provider.getConfigJson() != null && !provider.getConfigJson().isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(provider.getConfigJson());
                voice = textOrNull(node, "voice");
                String configuredFormat = textOrNull(node, "responseFormat");
                if (configuredFormat != null) {
                    responseFormat = configuredFormat;
                }
                if (node.hasNonNull("speed") && node.get("speed").isNumber()) {
                    speed = node.get("speed").asDouble();
                }
                instructions = textOrNull(node, "instructions");
            } catch (Exception ignored) {
            }
        }
        if (voice == null || voice.isBlank()) {
            voice = defaultVoiceForLanguage(language);
        }
        return new OpenAiSpeechClient.SpeechOptions(voice, responseFormat, speed, instructions);
    }

    private String resolveSpeechModel(ModelProvider provider) {
        String configured = extractProviderConfigText(provider, "ttsModel");
        if (configured == null) {
            configured = extractProviderConfigText(provider, "speechModel");
        }
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String modelName = provider.getModelName();
        if (modelName != null && !modelName.isBlank() && !looksLikeTranscriptionModel(modelName)) {
            return modelName.trim();
        }
        throw new BusinessException("配音模型未配置，请在语音提供商中填写真实 TTS 模型");
    }

    private String resolveTranscriptionModel(ModelProvider provider) {
        String configuredModel = extractProviderConfigText(provider, "transcriptionModel");
        if (configuredModel != null && !configuredModel.isBlank()) {
            return configuredModel;
        }
        if (provider.getModelName() != null
                && !provider.getModelName().isBlank()
                && looksLikeTranscriptionModel(provider.getModelName())) {
            return provider.getModelName().trim();
        }
        throw new BusinessException("转写模型未配置，请在语音提供商中填写字幕转写模型");
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        String value = node.get(fieldName).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultVoiceForLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "alloy";
        }
        String normalized = language.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("zh")) {
            return "alloy";
        }
        return "alloy";
    }

    private String extractProviderConfigText(ModelProvider provider, String fieldName) {
        if (provider.getConfigJson() == null || provider.getConfigJson().isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(provider.getConfigJson());
            return textOrNull(node, fieldName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveFfmpegCommand() {
        return ffmpegPath == null || ffmpegPath.isBlank() ? "ffmpeg" : ffmpegPath.trim();
    }

    private String buildSafeBaseName(String fileName) {
        String original = fileName == null || fileName.isBlank() ? "asset" : fileName;
        String withoutExt = original.replaceFirst("\\.[^.]+$", "");
        String sanitized = withoutExt.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]+", "_");
        return sanitized.isBlank() ? "asset" : sanitized;
    }

    private String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "未返回详细日志";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 220) {
            return compact;
        }
        return compact.substring(0, 220) + "...";
    }

    private String readTextFileQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private void deleteVoiceFile(String voiceUrl) {
        if (voiceUrl == null || voiceUrl.isBlank()) {
            return;
        }
        List<Path> targets = uploadStorageResolver.resolveLocalUploadPaths(voiceUrl);
        for (Path target : targets) {
            try {
                Files.deleteIfExists(target);
            } catch (Exception ignored) {
            }
        }
    }

    private SubtitleVO toVO(Subtitle sub) {
        SubtitleVO vo = VoMapper.copy(sub, SubtitleVO.class);
        vo.setVoiceUrl(uploadAccessSignatureService.signProjectUrl(vo.getVoiceUrl(), sub.getProjectId()));
        return vo;
    }

    private Asset requireAccessibleAsset(Long assetId) {
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            throw new BusinessException("资产不存在");
        }
        projectAccessGuard.assertAccess(asset.getProjectId());
        return asset;
    }
}
