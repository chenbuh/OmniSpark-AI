package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
    private Integer quotaLimit;
    private Integer quotaUsed;
    /** 仅管理员创建/重置且系统随机生成密码时返回,供管理员转交给用户;其余场景为 null。 */
    private String initialPassword;
}
