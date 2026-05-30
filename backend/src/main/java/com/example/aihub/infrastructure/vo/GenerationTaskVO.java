package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class GenerationTaskVO {
    private Long id;
    private Long projectId;
    private Long providerId;
    private String taskType;
    private String prompt;
    private String negativePrompt;
    private String status;
    private Integer progress;
    private String progressText;
    private String modelName;
    private Long resultAssetId;
    private String errorMessage;
    private String requestJson;
    private String responseJson;
    private java.time.LocalDateTime createdAt;
}
