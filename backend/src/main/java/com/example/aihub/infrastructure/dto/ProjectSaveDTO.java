package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectSaveDTO {
    @NotBlank
    private String name;

    private String description;
}
