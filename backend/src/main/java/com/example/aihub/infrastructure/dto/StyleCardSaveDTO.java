package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StyleCardSaveDTO {
    @NotBlank
    private String name;

    @NotBlank
    private String type;

    @NotBlank
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
}
