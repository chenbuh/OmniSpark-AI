package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ProjectExportVO {
    private String version;
    private String exportedAt;
    private String sourceBuildTime;
    private String sourceBranch;
    private String sourceCommitSha;
    private String canaryToken;
    private ProjectVO project;
    private List<ModelProviderVO> providers;
    private List<PromptTemplateVO> promptTemplates;
    private List<StyleCardVO> styleCards;
    private List<Map<String, Object>> workflows;
    private List<Map<String, Object>> assets;
}
