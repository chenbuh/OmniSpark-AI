package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeamMemberInviteDTO {
    @NotNull
    private Long teamId;

    @NotBlank
    private String username;

    private String role;
}
