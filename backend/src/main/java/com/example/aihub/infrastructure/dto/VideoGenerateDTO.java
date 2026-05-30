package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class VideoGenerateDTO {
    @NotNull
    private Long projectId;

    @NotNull
    private Long providerId;

    @NotBlank
    private String prompt;

    private String modelName;
    private Long sourceAssetId;
    private Long endAssetId;
    private String duration;
    private Map<String, Object> options;
}
