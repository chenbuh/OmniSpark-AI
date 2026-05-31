package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class PasswordPublicKeyVO {
    private String algorithm;
    private String publicKey;
}
