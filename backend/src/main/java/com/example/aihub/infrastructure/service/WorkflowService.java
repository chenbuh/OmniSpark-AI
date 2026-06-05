package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.security.UploadAccessSignatureService;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.ImageGenerateDTO;
import com.example.aihub.infrastructure.dto.SubtitleGenerateDTO;
import com.example.aihub.infrastructure.dto.VideoGenerateDTO;
import com.example.aihub.infrastructure.dto.WorkflowSaveDTO;
import com.example.aihub.infrastructure.entity.Workflow;
import com.example.aihub.infrastructure.entity.WorkflowRun;
import com.example.aihub.infrastructure.mapper.WorkflowMapper;
import com.example.aihub.infrastructure.mapper.WorkflowRunMapper;
import com.example.aihub.infrastructure.vo.GenerationTaskVO;
import com.example.aihub.infrastructure.vo.SubtitleVO;
import com.example.aihub.infrastructure.vo.WorkflowRunVO;
import com.example.aihub.infrastructure.vo.WorkflowVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowService {
    private static final Set<String> SUPPORTED_STEP_TYPES = Set.of("image", "video", "subtitle");

    private final WorkflowMapper workflowMapper;
    private final WorkflowRunMapper runMapper;
    private final GenerationService generationService;
    private final SubtitleService subtitleService;
    private final ObjectMapper objectMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;
    private final UploadAccessSignatureService uploadAccessSignatureService;

    public List<WorkflowVO> list(Long projectId, int limit) {
        LambdaQueryWrapper<Workflow> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            projectAccessGuard.assertAccess(projectId);
            wrapper.eq(Workflow::getProjectId, projectId);
        } else {
            List<Long> accessibleIds = projectAccessGuard.accessibleProjectIds();
            if (accessibleIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(Workflow::getProjectId, accessibleIds);
        }
        wrapper.orderByDesc(Workflow::getId);
        wrapper.last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100));
        return workflowMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public WorkflowVO get(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException("工作流不存在");
        }
        projectAccessGuard.assertAccess(workflow.getProjectId());
        return toVO(workflow);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowVO create(WorkflowSaveDTO dto) {
        projectAccessGuard.assertAccess(dto.getProjectId());
        Workflow workflow = new Workflow();
        workflow.setProjectId(dto.getProjectId());
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        workflow.setStepsJson(validateAndNormalizeStepsJson(dto.getStepsJson()));
        workflow.setStatus(1);
        workflowMapper.insert(workflow);
        return toVO(workflow);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowVO update(Long id, WorkflowSaveDTO dto) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException("工作流不存在");
        }
        projectAccessGuard.assertAccess(workflow.getProjectId());
        projectAccessGuard.assertAccess(dto.getProjectId());
        workflow.setProjectId(dto.getProjectId());
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        workflow.setStepsJson(validateAndNormalizeStepsJson(dto.getStepsJson()));
        workflowMapper.updateById(workflow);
        return toVO(workflow);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Workflow workflow = workflowMapper.selectById(id);
        if (workflow == null) {
            throw new BusinessException("工作流不存在");
        }
        projectAccessGuard.assertAccess(workflow.getProjectId());
        workflowMapper.deleteById(id);
        runMapper.delete(new LambdaQueryWrapper<WorkflowRun>().eq(WorkflowRun::getWorkflowId, id));
    }

    public List<WorkflowRunVO> listRuns(Long workflowId, int limit) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BusinessException("工作流不存在");
        }
        projectAccessGuard.assertAccess(workflow.getProjectId());
        return runMapper.selectList(new LambdaQueryWrapper<WorkflowRun>()
                        .eq(WorkflowRun::getWorkflowId, workflowId)
                        .orderByDesc(WorkflowRun::getId)
                        .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)))
                .stream()
                .map(this::toRunVO)
                .toList();
    }

    public WorkflowRunVO getRun(Long runId) {
        WorkflowRun run = runMapper.selectById(runId);
        if (run == null) {
            throw new BusinessException("运行记录不存在");
        }
        projectAccessGuard.assertAccess(run.getProjectId());
        return toRunVO(run);
    }

    @Transactional(rollbackFor = Exception.class)
    public WorkflowRunVO execute(Long workflowId) {
        Workflow workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw new BusinessException("工作流不存在");
        }
        projectAccessGuard.assertAccess(workflow.getProjectId());

        ArrayNode steps = parseStepsArray(workflow.getStepsJson());
        if (steps.isEmpty()) {
            throw new BusinessException("工作流中没有定义步骤");
        }

        WorkflowRun run = new WorkflowRun();
        run.setWorkflowId(workflowId);
        run.setProjectId(workflow.getProjectId());
        run.setStatus("running");
        run.setCurrentStep(0);
        run.setStartedAt(LocalDateTime.now());
        runMapper.insert(run);

        ArrayNode stepResults = objectMapper.createArrayNode();
        WorkflowExecutionContext context = new WorkflowExecutionContext(workflow.getProjectId());

        try {
            for (int i = 0; i < steps.size(); i++) {
                JsonNode stepNode = steps.get(i);
                String stepType = textValue(stepNode, "type");

                run.setCurrentStep(i);
                runMapper.updateById(run);

                ObjectNode stepResult = objectMapper.createObjectNode();
                stepResult.put("stepIndex", i);
                stepResult.put("stepType", stepType);
                stepResult.set("params", stepNode);

                try {
                    ObjectNode executionResult = executeStep(stepNode, context);
                    stepResult.put("status", "success");
                    stepResult.setAll(executionResult);
                    if (!stepResult.has("message")) {
                        stepResult.put("message", "步骤 " + (i + 1) + " 执行完成");
                    }
                    stepResults.add(stepResult);
                } catch (Exception ex) {
                    stepResult.put("status", "failed");
                    stepResult.put("message", ex.getMessage());
                    stepResults.add(stepResult);
                    throw ex;
                }
            }

            run.setStatus("success");
            run.setStepsResultJson(stepResults.toString());
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            return toRunVO(run);
        } catch (Exception ex) {
            run.setStatus("failed");
            run.setErrorMessage(ex.getMessage());
            run.setStepsResultJson(stepResults.toString());
            run.setFinishedAt(LocalDateTime.now());
            runMapper.updateById(run);
            throw new BusinessException("工作流执行失败: " + ex.getMessage());
        }
    }

    private ObjectNode executeStep(JsonNode stepNode, WorkflowExecutionContext context) {
        String stepType = textValue(stepNode, "type");
        if (!SUPPORTED_STEP_TYPES.contains(stepType)) {
            throw new BusinessException("不支持的步骤类型: " + stepType);
        }
        return switch (stepType) {
            case "image" -> executeImageStep(stepNode, context);
            case "video" -> executeVideoStep(stepNode, context);
            case "subtitle" -> executeSubtitleStep(stepNode, context);
            default -> throw new BusinessException("未知步骤类型: " + stepType);
        };
    }

    private ObjectNode executeImageStep(JsonNode stepNode, WorkflowExecutionContext context) {
        Long providerId = longValue(stepNode, "providerId");
        if (providerId == null) {
            throw new BusinessException("生图步骤缺少 providerId");
        }

        String prompt = firstNonBlank(textValue(stepNode, "prompt"), context.lastPrompt);
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("生图步骤缺少 prompt");
        }

        ImageGenerateDTO dto = new ImageGenerateDTO();
        dto.setProjectId(context.projectId);
        dto.setProviderId(providerId);
        dto.setPrompt(prompt);
        dto.setModelName(textValue(stepNode, "modelName"));
        dto.setNegativePrompt(textValue(stepNode, "negativePrompt"));
        dto.setSize(textValue(stepNode, "size"));
        dto.setCount(integerValue(stepNode, "count"));
        dto.setMaskAssetId(longValue(stepNode, "maskAssetId"));

        List<Long> referenceAssetIds = longListValue(stepNode, "referenceAssetIds");
        if (referenceAssetIds.isEmpty() && booleanValue(stepNode, "usePreviousAsReference") && context.lastAssetId != null) {
            referenceAssetIds = List.of(context.lastAssetId);
        }
        if (!referenceAssetIds.isEmpty()) {
            dto.setReferenceAssetIds(referenceAssetIds);
        }

        Map<String, Object> options = objectValue(stepNode, "options");
        if (!options.isEmpty()) {
            dto.setOptions(options);
        }

        GenerationTaskVO task = dto.getMaskAssetId() != null
                ? generationService.generateInpaint(dto)
                : generationService.generateImage(dto);

        context.captureTask(task);
        context.lastImageAssetId = task.getResultAssetId();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("taskId", task.getId());
        result.put("assetId", task.getResultAssetId());
        result.put("message", "已完成生图步骤");
        if (!referenceAssetIds.isEmpty()) {
            result.put("referenceAssetId", referenceAssetIds.get(0));
        }
        return result;
    }

    private ObjectNode executeVideoStep(JsonNode stepNode, WorkflowExecutionContext context) {
        Long providerId = longValue(stepNode, "providerId");
        if (providerId == null) {
            throw new BusinessException("生视频步骤缺少 providerId");
        }

        String prompt = firstNonBlank(textValue(stepNode, "prompt"), context.lastPrompt);
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("生视频步骤缺少 prompt");
        }

        VideoGenerateDTO dto = new VideoGenerateDTO();
        dto.setProjectId(context.projectId);
        dto.setProviderId(providerId);
        dto.setPrompt(prompt);
        dto.setModelName(textValue(stepNode, "modelName"));
        dto.setDuration(firstNonBlank(textValue(stepNode, "duration"), "5s"));

        Long sourceAssetId = longValue(stepNode, "sourceAssetId");
        if (sourceAssetId == null) {
            sourceAssetId = context.lastImageAssetId != null ? context.lastImageAssetId : context.lastAssetId;
        }
        if (sourceAssetId != null) {
            dto.setSourceAssetId(sourceAssetId);
        }

        Long endAssetId = longValue(stepNode, "endAssetId");
        if (endAssetId != null) {
            dto.setEndAssetId(endAssetId);
        }

        Map<String, Object> options = objectValue(stepNode, "options");
        if (!options.isEmpty()) {
            dto.setOptions(options);
        }

        GenerationTaskVO task = generationService.generateVideo(dto);
        context.captureTask(task);
        context.lastVideoAssetId = task.getResultAssetId();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("taskId", task.getId());
        result.put("assetId", task.getResultAssetId());
        result.put("message", "已完成视频步骤");
        if (dto.getSourceAssetId() != null) {
            result.put("sourceAssetId", dto.getSourceAssetId());
        }
        return result;
    }

    private ObjectNode executeSubtitleStep(JsonNode stepNode, WorkflowExecutionContext context) {
        Long assetId = longValue(stepNode, "assetId");
        if (assetId == null) {
            assetId = context.lastVideoAssetId != null ? context.lastVideoAssetId : context.lastAssetId;
        }
        if (assetId == null) {
            throw new BusinessException("字幕步骤缺少目标视频资产");
        }

        String prompt = firstNonBlank(textValue(stepNode, "prompt"), context.lastPrompt);
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException("字幕步骤缺少 prompt");
        }

        SubtitleGenerateDTO dto = new SubtitleGenerateDTO();
        dto.setAssetId(assetId);
        dto.setProjectId(context.projectId);
        dto.setLanguage(firstNonBlank(textValue(stepNode, "language"), "zh"));
        dto.setPrompt(prompt);

        SubtitleVO subtitle = subtitleService.generate(dto);
        if (booleanValue(stepNode, "voice")) {
            subtitle = subtitleService.generateVoice(subtitle.getId());
        }
        context.lastSubtitleId = subtitle.getId();

        ObjectNode result = objectMapper.createObjectNode();
        result.put("subtitleId", subtitle.getId());
        result.put("assetId", assetId);
        result.put("message", booleanValue(stepNode, "voice") ? "字幕与配音已生成" : "字幕已生成");
        if (subtitle.getVoiceUrl() != null) {
            result.put("voiceUrl", uploadAccessSignatureService.signProjectUrl(subtitle.getVoiceUrl(), subtitle.getProjectId()));
        }
        return result;
    }

    private String validateAndNormalizeStepsJson(String stepsJson) {
        try {
            ArrayNode steps = parseStepsArray(stepsJson);
            if (steps.isEmpty()) {
                throw new BusinessException("至少需要定义一个步骤");
            }
            for (int i = 0; i < steps.size(); i++) {
                JsonNode stepNode = steps.get(i);
                if (!stepNode.isObject()) {
                    throw new BusinessException("步骤 " + (i + 1) + " 必须是对象");
                }
                String type = textValue(stepNode, "type");
                if (type == null || type.isBlank()) {
                    throw new BusinessException("步骤 " + (i + 1) + " 缺少 type");
                }
                if (!SUPPORTED_STEP_TYPES.contains(type)) {
                    throw new BusinessException("步骤 " + (i + 1) + " 使用了不支持的类型: " + type);
                }
            }
            return objectMapper.writeValueAsString(steps);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("步骤配置解析失败: " + ex.getMessage());
        }
    }

    private ArrayNode parseStepsArray(String stepsJson) {
        try {
            JsonNode root = objectMapper.readTree(stepsJson);
            if (!(root instanceof ArrayNode arrayNode)) {
                throw new BusinessException("步骤定义必须是 JSON 数组");
            }
            return arrayNode;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("步骤配置解析失败: " + ex.getMessage());
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : Long.valueOf(text);
    }

    private Integer integerValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : Integer.valueOf(text);
    }

    private boolean booleanValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return false;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        return Boolean.parseBoolean(value.asText());
    }

    private Map<String, Object> objectValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private List<Long> longListValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isArray()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : value) {
            String text = item.asText();
            if (text != null && !text.isBlank()) {
                ids.add(Long.valueOf(text));
            }
        }
        return ids;
    }

    private String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first : fallback;
    }

    private WorkflowVO toVO(Workflow workflow) {
        return VoMapper.copy(workflow, WorkflowVO.class);
    }

    private WorkflowRunVO toRunVO(WorkflowRun run) {
        return VoMapper.copy(run, WorkflowRunVO.class);
    }

    private static class WorkflowExecutionContext {
        private final Long projectId;
        private Long lastTaskId;
        private Long lastAssetId;
        private Long lastImageAssetId;
        private Long lastVideoAssetId;
        private Long lastSubtitleId;
        private String lastPrompt;

        private WorkflowExecutionContext(Long projectId) {
            this.projectId = projectId;
        }

        private void captureTask(GenerationTaskVO task) {
            this.lastTaskId = task.getId();
            this.lastAssetId = task.getResultAssetId();
            this.lastPrompt = task.getPrompt();
        }
    }
}
