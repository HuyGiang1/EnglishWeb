package com.englishflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EnglishFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnglishFlowApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public java.time.Clock clock() {
        return java.time.Clock.systemUTC();
    }
}
