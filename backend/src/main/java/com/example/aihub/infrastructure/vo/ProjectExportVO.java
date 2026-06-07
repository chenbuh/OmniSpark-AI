package com.example.aihub.infrastructure.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectExportVO {
    private String version;
    private String exportedAt;
    private String sourceBuildTime;
    private String sourceBranch;
    private String sourceCommitSha;
    private String exportScope;
    private String exportNotice;
    private Integer exportedAssetMetadataCount;
    private String canaryToken;
    private ProjectVO project;
    private List<ModelProviderVO> providers;
    private List<Map<String, Object>> workflows;
    private List<Map<String, Object>> assets;
}
