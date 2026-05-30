package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeamSaveDTO {
    @NotBlank
    private String name;

    private String description;
}
