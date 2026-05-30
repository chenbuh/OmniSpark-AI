package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ImageGenerateDTO {
    @NotNull
    private Long projectId;

    @NotNull
    private Long providerId;

    @NotBlank
    private String prompt;

    private String modelName;
    private String negativePrompt;
    private List<Long> referenceAssetIds;
    private Long maskAssetId;
    private String size;
    private Integer count;
    private Map<String, Object> options;
}
