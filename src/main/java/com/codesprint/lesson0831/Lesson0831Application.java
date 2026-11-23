package com.codesprint.lesson0831;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Lesson0831Application {

    public static void main(String[] args) {
        SpringApplication.run(Lesson0831Application.class, args);
    }
}
