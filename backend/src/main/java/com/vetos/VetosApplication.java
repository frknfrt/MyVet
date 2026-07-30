package com.vetos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class VetosApplication {

    public static void main(String[] args) {
        SpringApplication.run(VetosApplication.class, args);
    }
}
