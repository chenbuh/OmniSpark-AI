package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private UserVO userInfo;
}
