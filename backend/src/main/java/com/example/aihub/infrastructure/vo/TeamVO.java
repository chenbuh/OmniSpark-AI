package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TeamVO {
    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private String ownerName;
    private String avatar;
    private Integer status;
    private Integer memberCount;
    private LocalDateTime createdAt;
}
