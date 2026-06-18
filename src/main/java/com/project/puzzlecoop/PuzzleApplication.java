package com.project.puzzlecoop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PuzzleApplication {
    public static void main(String[] args) {
        SpringApplication.run(PuzzleApplication.class, args);
    }
}