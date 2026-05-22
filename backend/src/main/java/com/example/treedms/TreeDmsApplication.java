package com.example.treedms;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@MapperScan("com.example.treedms.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
public class TreeDmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(TreeDmsApplication.class, args);
    }
}
