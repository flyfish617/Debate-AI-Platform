package com.debateai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DebateAI后端应用启动类
 */
@SpringBootApplication
@EnableScheduling
public class DebateAiApplication {

    /**
     * 启动Spring Boot应用
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(DebateAiApplication.class, args);
    }
}
