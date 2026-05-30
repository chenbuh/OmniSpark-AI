package com.example.aihub;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.aihub.infrastructure.mapper")
public class AiHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiHubApplication.class, args);
    }
}
