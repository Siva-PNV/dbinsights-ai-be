package edu.dbinsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DbInsightApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbInsightApplication.class, args);
    }
}