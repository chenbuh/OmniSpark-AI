package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PromptOptimizeDTO {
    @NotNull
    private Long projectId;

    private Long providerId;

    private String modelName;

    @NotBlank
    private String prompt;
}
