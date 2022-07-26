package com.cubetrek;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CubetrekApplication {
    public static void main(String[] args) {
        SpringApplication.run(CubetrekApplication.class, args);
    }
}
