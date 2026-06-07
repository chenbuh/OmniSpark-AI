package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginTotpDTO {
    @NotBlank
    private String loginTicket;

    @NotBlank
    private String totpCode;
}
