package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StyleCardVO {
    private Long id;
    private Long projectId;
    private String name;
    private String type;
    private String content;
    private String negativePrompt;
    private String modelName;
    private Long providerId;
    private Long refAssetId;
    private Double cfg;
    private Integer steps;
    private String size;
    private String paramsJson;
    private String previewUrl;
    private String tag;
    private Integer status;
    private LocalDateTime createdAt;
}
