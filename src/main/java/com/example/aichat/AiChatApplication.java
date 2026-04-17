package com.example.aichat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.example.aichat.config.ModelProperties;

@SpringBootApplication
@EnableConfigurationProperties(ModelProperties.class)
@EnableScheduling
public class AiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
    }
}
