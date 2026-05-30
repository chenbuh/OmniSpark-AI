package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowSaveDTO {
    @NotNull
    private Long projectId;

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String stepsJson;
}
