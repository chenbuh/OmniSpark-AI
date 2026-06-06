package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.meta.BuildMetadataService;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.ProjectSaveDTO;
import com.example.aihub.infrastructure.dto.StyleCardSaveDTO;
import com.example.aihub.infrastructure.entity.*;
import com.example.aihub.infrastructure.mapper.*;
import com.example.aihub.infrastructure.vo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectMapper projectMapper;
    private final ProjectShareMapper shareMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final ModelProviderMapper providerMapper;
    private final PromptTemplateMapper templateMapper;
    private final StyleCardMapper styleCardMapper;
    private final WorkflowMapper workflowMapper;
    private final WorkflowRunMapper workflowRunMapper;
    private final GenerationTaskMapper generationTaskMapper;
    private final QuotaRecordMapper quotaRecordMapper;
    private final AssetMapper assetMapper;
    private final ObjectMapper objectMapper;
    private final com.example.aihub.common.security.ProjectAccessGuard projectAccessGuard;
    private final AssetService assetService;
    private final SubtitleService subtitleService;
    private final BuildMetadataService buildMetadataService;

    // ===== 原有 listMine / create / update / delete 保持不变 =====

    public PageResult<ProjectVO> pageMine(long page, long pageSize) {
        Long userId = SecurityUtil.loginUserId();
        List<ProjectVO> allProjects = listAccessibleProjects(userId);
        long total = allProjects.size();
        long safePage = PagingUtil.normalizePage(page);
        long safePageSize = PagingUtil.clampPageSize(pageSize, 20);
        if (total == 0) {
            return new PageResult<>(0, 0, List.of());
        }
        long pages = (total + safePageSize - 1) / safePageSize;
        long fromIndex = (safePage - 1) * safePageSize;
        if (fromIndex >= total) {
            return new PageResult<>(total, pages, List.of());
        }
        int start = (int) fromIndex;
        int end = (int) Math.min(total, fromIndex + safePageSize);
        return new PageResult<>(total, pages, allProjects.subList(start, end));
    }

    public List<ProjectVO> listMine(int limit) {
        Long userId = SecurityUtil.loginUserId();
        int safeLimit = PagingUtil.clampLimit(limit, 100, 100);
        List<ProjectVO> accessibleProjects = listAccessibleProjects(userId);
        if (accessibleProjects.size() <= safeLimit) {
            return accessibleProjects;
        }
        return accessibleProjects.subList(0, safeLimit);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectVO create(ProjectSaveDTO dto) {
        Project project = new Project();
        project.setUserId(SecurityUtil.loginUserId());
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        project.setStatus(1);
        projectMapper.insert(project);
        return toProjectVO(project, SecurityUtil.loginUserId(), null);
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectVO update(Long id, ProjectSaveDTO dto) {
        Project project = projectMapper.selectById(id);
        if (project == null) throw new BusinessException("项目不存在");
        Long userId = SecurityUtil.loginUserId();
        if (!project.getUserId().equals(userId)) {
            boolean isAdmin = shareMapper.selectCount(new LambdaQueryWrapper<ProjectShare>()
                    .eq(ProjectShare::getProjectId, id)
                    .in(ProjectShare::getTeamId, getMyTeamIds(userId))
                    .in(ProjectShare::getPermission, "admin", "edit")) > 0;
            if (!isAdmin) throw new BusinessException("没有编辑该项目的权限");
        }
        project.setName(dto.getName());
        project.setDescription(dto.getDescription());
        projectMapper.updateById(project);
        return toProjectVO(project, userId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) throw new BusinessException("项目不存在");
        if (!project.getUserId().equals(SecurityUtil.loginUserId()))
            throw new BusinessException("只有项目所有者才能删除项目");

        subtitleService.deleteByProjectId(id);
        assetService.deleteByProjectId(id);
        workflowRunMapper.delete(new LambdaQueryWrapper<WorkflowRun>().eq(WorkflowRun::getProjectId, id));
        workflowMapper.delete(new LambdaQueryWrapper<Workflow>().eq(Workflow::getProjectId, id));
        generationTaskMapper.delete(new LambdaQueryWrapper<GenerationTask>().eq(GenerationTask::getProjectId, id));
        quotaRecordMapper.delete(new LambdaQueryWrapper<QuotaRecord>().eq(QuotaRecord::getProjectId, id));
        styleCardMapper.delete(new LambdaQueryWrapper<StyleCard>().eq(StyleCard::getProjectId, id));
        templateMapper.delete(new LambdaQueryWrapper<PromptTemplate>().eq(PromptTemplate::getProjectId, id));
        providerMapper.delete(new LambdaQueryWrapper<ModelProvider>().eq(ModelProvider::getProjectId, id));
        shareMapper.delete(new LambdaQueryWrapper<ProjectShare>().eq(ProjectShare::getProjectId, id));
        projectMapper.deleteById(id);
    }

    private List<Long> getMyTeamIds(Long userId) {
        return teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1))
                .stream().map(TeamMember::getTeamId).toList();
    }

    private List<ProjectVO> listAccessibleProjects(Long userId) {
        Map<Long, Project> projectById = new HashMap<>();
        Map<Long, String> sharedPermissionByProjectId = new HashMap<>();

        List<Project> ownedProjects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getUserId, userId)
                .orderByDesc(Project::getId));
        ownedProjects.forEach(project -> projectById.put(project.getId(), project));

        List<TeamMember> memberships = teamMemberMapper.selectList(
                new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, 1)
                        .orderByDesc(TeamMember::getId));
        if (!memberships.isEmpty()) {
            Set<Long> teamIds = memberships.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
            List<ProjectShare> shares = shareMapper.selectList(
                    new LambdaQueryWrapper<ProjectShare>()
                            .in(ProjectShare::getTeamId, teamIds)
                            .orderByDesc(ProjectShare::getId));
            if (!shares.isEmpty()) {
                Set<Long> missingProjectIds = new LinkedHashSet<>();
                for (ProjectShare share : shares) {
                    sharedPermissionByProjectId.merge(
                            share.getProjectId(),
                            normalizeSharedPermission(share.getPermission()),
                            this::higherPermission
                    );
                    if (!projectById.containsKey(share.getProjectId())) {
                        missingProjectIds.add(share.getProjectId());
                    }
                }
                if (!missingProjectIds.isEmpty()) {
                    projectMapper.selectList(new LambdaQueryWrapper<Project>()
                                    .in(Project::getId, missingProjectIds))
                            .forEach(project -> projectById.put(project.getId(), project));
                }
            }
        }

        return projectById.values().stream()
                .sorted(Comparator.comparing(Project::getId).reversed())
                .map(item -> toProjectVO(item, userId, sharedPermissionByProjectId.get(item.getId())))
                .toList();
    }

    private ProjectVO toProjectVO(Project project, Long currentUserId, String sharedPermission) {
        ProjectVO vo = VoMapper.copy(project, ProjectVO.class);
        boolean ownedByCurrentUser = project.getUserId() != null && project.getUserId().equals(currentUserId);
        vo.setOwnedByCurrentUser(ownedByCurrentUser);
        vo.setAccessPermission(ownedByCurrentUser ? "owner" : resolveSharedPermission(project.getId(), currentUserId, sharedPermission));
        return vo;
    }

    private String resolveSharedPermission(Long projectId, Long userId, String preferredPermission) {
        String normalizedPreferredPermission = normalizeOptionalText(preferredPermission).toLowerCase(Locale.ROOT);
        if (!normalizedPreferredPermission.isBlank()) {
            return normalizeSharedPermission(normalizedPreferredPermission);
        }
        List<Long> teamIds = getMyTeamIds(userId);
        if (teamIds.isEmpty()) {
            return "view";
        }
        return shareMapper.selectList(new LambdaQueryWrapper<ProjectShare>()
                        .eq(ProjectShare::getProjectId, projectId)
                        .in(ProjectShare::getTeamId, teamIds))
                .stream()
                .map(ProjectShare::getPermission)
                .reduce("view", this::higherPermission);
    }

    private String normalizeSharedPermission(String permission) {
        String normalized = normalizeOptionalText(permission).toLowerCase(Locale.ROOT);
        if (normalized.equals("admin") || normalized.equals("edit")) {
            return normalized;
        }
        return "view";
    }

    private String higherPermission(String left, String right) {
        return permissionLevel(left) >= permissionLevel(right) ? normalizeSharedPermission(left) : normalizeSharedPermission(right);
    }

    private int permissionLevel(String permission) {
        return switch (normalizeSharedPermission(permission)) {
            case "admin" -> 3;
            case "edit" -> 2;
            default -> 1;
        };
    }

    // ===== 导出/导入 =====

    public ProjectExportVO exportProject(Long projectId) {
        projectAccessGuard.assertAccess(projectId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) throw new BusinessException("项目不存在");

        ProjectExportVO vo = new ProjectExportVO();
        vo.setVersion(buildMetadataService.currentVersionForDisplay());
        vo.setExportedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        vo.setSourceBuildTime(buildMetadataService.buildTimeOrBlank());
        vo.setSourceBranch(buildMetadataService.currentBranch());
        vo.setSourceCommitSha(buildMetadataService.currentCommitSha());
        vo.setAssetTransferMode("metadata-only");
        vo.setAssetExportNotice("导出文件中的 assets 仅包含资产元数据，不包含可自动恢复的二进制文件；公共提示词模板与公共风格卡属于全局内容，不跟随项目导出。导入后请手动重新上传相关素材。");
        vo.setProject(VoMapper.copy(project, ProjectVO.class));
        vo.setProviders(providerMapper.selectList(
                new LambdaQueryWrapper<ModelProvider>().eq(ModelProvider::getProjectId, projectId))
                .stream().map(p -> VoMapper.copy(p, ModelProviderVO.class)).toList());
        vo.setPromptTemplates(List.of());
        vo.setStyleCards(List.of());
        vo.setWorkflows(workflowMapper.selectList(
                new LambdaQueryWrapper<Workflow>().eq(Workflow::getProjectId, projectId))
                .stream().map(w -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", w.getId());
                    m.put("name", w.getName());
                    m.put("description", w.getDescription());
                    m.put("stepsJson", w.getStepsJson());
                    m.put("status", w.getStatus());
                    return m;
                }).toList());
        List<Map<String, Object>> exportedAssets = assetMapper.selectList(
                new LambdaQueryWrapper<Asset>().eq(Asset::getProjectId, projectId))
                .stream().map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("assetType", a.getAssetType());
                    m.put("fileName", a.getFileName());
                    m.put("fileUrl", a.getFileUrl());
                    m.put("mimeType", a.getMimeType());
                    m.put("fileSize", a.getFileSize());
                    m.put("prompt", a.getPrompt());
                    m.put("modelName", a.getModelName());
                    return m;
                }).toList();
        vo.setAssets(exportedAssets);
        vo.setExportedAssetCount(exportedAssets.size());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProjectImportResult importProject(ProjectExportVO data) {
        if (data.getProject() == null) throw new BusinessException("导入数据缺少项目信息");
        if (normalizeOptionalText(data.getVersion()).isBlank()) throw new BusinessException("导入数据缺少导出版本信息");
        String importedProjectName = normalizeOptionalText(data.getProject().getName());
        if (importedProjectName.isBlank()) throw new BusinessException("导入项目名称不能为空");

        // 创建项目
        Project project = new Project();
        project.setUserId(SecurityUtil.loginUserId());
        project.setName(importedProjectName + " (导入)");
        project.setDescription(data.getProject().getDescription());
        project.setStatus(1);
        projectMapper.insert(project);
        Long newId = project.getId();

        // 导入模型提供商
        if (data.getProviders() != null) {
            for (ModelProviderVO p : data.getProviders()) {
                ModelProvider entity = new ModelProvider();
                entity.setProjectId(newId);
                entity.setName(p.getName());
                entity.setType(p.getType());
                entity.setBaseUrl(p.getBaseUrl());
                entity.setApiKey(p.getApiKey());
                entity.setModelName(p.getModelName());
                entity.setEnabled(normalizeStatusFlag(p.getEnabled(), 1));
                entity.setIsDefault(normalizeStatusFlag(p.getIsDefault(), 0));
                entity.setConfigJson(p.getConfigJson());
                providerMapper.insert(entity);
            }
        }

        // 导入工作流
        if (data.getWorkflows() != null) {
            for (Map<String, Object> w : data.getWorkflows()) {
                ImportedWorkflowRecord workflowRecord = requireImportedWorkflowRecord(w);
                Workflow entity = new Workflow();
                entity.setProjectId(newId);
                entity.setName(workflowRecord.name());
                entity.setDescription(workflowRecord.description());
                entity.setStepsJson(workflowRecord.stepsJson());
                entity.setStatus(workflowRecord.status());
                workflowMapper.insert(entity);
            }
        }

        int skippedAssetCount = data.getAssets() == null ? 0 : data.getAssets().size();
        boolean hasLegacyPromptTemplates = data.getPromptTemplates() != null && !data.getPromptTemplates().isEmpty();
        boolean hasLegacyStyleCards = data.getStyleCards() != null && !data.getStyleCards().isEmpty();
        String importNotice = buildProjectImportNotice(skippedAssetCount, hasLegacyPromptTemplates, hasLegacyStyleCards);
        return new ProjectImportResult(
                newId,
                data.getProviders() == null ? 0 : data.getProviders().size(),
                0,
                0,
                data.getWorkflows() == null ? 0 : data.getWorkflows().size(),
                0,
                skippedAssetCount,
                importNotice
        );
    }

    private String buildProjectImportNotice(int skippedAssetCount, boolean hasLegacyPromptTemplates, boolean hasLegacyStyleCards) {
        List<String> notices = new ArrayList<>();
        if (skippedAssetCount > 0) {
            notices.add("导入文件中的资产仅包含元数据，当前不会自动恢复二进制文件，请手动重新上传。");
        }
        if (hasLegacyPromptTemplates || hasLegacyStyleCards) {
            notices.add("导入文件中的提示词模板和风格卡属于旧版项目内数据；当前版本已改为公共库内容，本次不会恢复到项目空间。");
        }
        return String.join(" ", notices);
    }

    private ImportedWorkflowRecord requireImportedWorkflowRecord(Map<String, Object> workflowData) {
        if (workflowData == null) {
            throw new BusinessException("导入工作流数据缺失");
        }
        String name = normalizeOptionalText(workflowData.get("name"));
        if (name.isBlank()) {
            throw new BusinessException("导入工作流名称不能为空");
        }
        String description = normalizeOptionalText(workflowData.get("description"));
        String stepsJson = normalizeOptionalText(workflowData.get("stepsJson"));
        if (stepsJson.isBlank()) {
            throw new BusinessException("导入工作流步骤不能为空");
        }
        try {
            if (!objectMapper.readTree(stepsJson).isArray()) {
                throw new BusinessException("导入工作流步骤格式不正确");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("导入工作流步骤格式不正确");
        }
        Integer status = normalizeStatusFlag(asInteger(workflowData.get("status")), 1);
        return new ImportedWorkflowRecord(name, description, stepsJson, status);
    }

    private Integer normalizeStatusFlag(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return value == 0 ? 0 : 1;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeOptionalText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record ImportedWorkflowRecord(String name, String description, String stepsJson, Integer status) {}

    public record ProjectImportResult(
            Long projectId,
            int importedProviderCount,
            int importedPromptTemplateCount,
            int importedStyleCardCount,
            int importedWorkflowCount,
            int importedAssetCount,
            int skippedAssetCount,
            String assetImportNotice
    ) {}
}
