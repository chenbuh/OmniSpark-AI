package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginTotpSetupDTO {
    @NotBlank
    private String setupTicket;

    @NotBlank
    private String totpCode;
}
