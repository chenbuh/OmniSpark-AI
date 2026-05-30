package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareSaveDTO {
    @NotNull
    private Long projectId;

    @NotNull
    private Long teamId;

    @NotBlank
    private String permission;
}
