package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubtitleGenerateDTO {
    @NotNull
    private Long assetId;

    @NotNull
    private Long projectId;

    private String language;

    private String prompt;
}
