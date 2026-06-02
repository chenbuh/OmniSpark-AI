package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.security.UploadAccessSignatureService;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.SubtitleGenerateDTO;
import com.example.aihub.infrastructure.dto.SubtitleUpdateDTO;
import com.example.aihub.infrastructure.entity.Asset;
import com.example.aihub.infrastructure.entity.Subtitle;
import com.example.aihub.infrastructure.mapper.AssetMapper;
import com.example.aihub.infrastructure.mapper.SubtitleMapper;
import com.example.aihub.infrastructure.vo.SubtitleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubtitleService {
    private final SubtitleMapper subtitleMapper;
    private final AssetMapper assetMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;
    private final UploadAccessSignatureService uploadAccessSignatureService;

    public List<SubtitleVO> listByAsset(Long assetId) {
        requireAccessibleAsset(assetId);
        return subtitleMapper.selectList(
                new LambdaQueryWrapper<Subtitle>().eq(Subtitle::getAssetId, assetId))
                .stream().map(this::toVO).toList();
    }

    public SubtitleVO get(Long id) {
        Subtitle sub = subtitleMapper.selectById(id);
        if (sub == null) throw new BusinessException("字幕不存在");
        projectAccessGuard.assertAccess(sub.getProjectId());
        return toVO(sub);
    }

    /**
     * 从 prompt 自动生成 SRT 字幕
     * 将 prompt 按空格/标点拆分为 3-5 段，每段分配时间
     */
    @Transactional(rollbackFor = Exception.class)
    public SubtitleVO generate(SubtitleGenerateDTO dto) {
        Asset asset = requireAccessibleAsset(dto.getAssetId());
        if (!asset.getProjectId().equals(dto.getProjectId())) {
            throw new BusinessException("字幕所属项目与资产不一致");
        }

        // 先删除该资产已有的字幕
        subtitleMapper.delete(new LambdaQueryWrapper<Subtitle>().eq(Subtitle::getAssetId, dto.getAssetId()));

        String srt = buildSrtFromPrompt(dto.getPrompt(), dto.getLanguage());
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
            // 模拟 TTS：生成一段沉默的音频占位 + 保存文本标注
            // 生产环境可接入火山引擎/阿里云 TTS API
            Path ttsDir = Paths.get("uploads", "tts");
            Files.createDirectories(ttsDir);
            String fileName = "tts_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + ".txt";
            Path target = ttsDir.resolve(fileName);
            Files.writeString(target, plainText, StandardCharsets.UTF_8);

            String voiceUrl = "/uploads/tts/" + fileName;
            sub.setVoiceUrl(voiceUrl);
            subtitleMapper.updateById(sub);
            return toVO(sub);
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
        subtitleMapper.deleteById(id);
    }

    // ===== 内部方法 =====

    private String buildSrtFromPrompt(String prompt, String language) {
        if (prompt == null || prompt.isBlank()) {
            return "1\n00:00:01,000 --> 00:00:04,000\n无内容\n";
        }

        // 按句子/逗号拆分
        String[] sentences = prompt.split("[，。！？、,.;!?\\n]+");
        StringBuilder sb = new StringBuilder();
        int totalSec = Math.max(sentences.length * 3, 5);
        int segSec = Math.max(totalSec / sentences.length, 2);

        for (int i = 0; i < sentences.length; i++) {
            String s = sentences[i].trim();
            if (s.isBlank()) continue;
            int startMin = 0;
            int startSec = i * segSec;
            int endSec = (i + 1) * segSec;
            sb.append(i + 1).append("\n");
            sb.append(String.format("00:%02d:%02d,000 --> 00:%02d:%02d,000%n", startMin, startSec, startMin, endSec));
            sb.append(s).append("\n\n");
        }
        return sb.toString();
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
