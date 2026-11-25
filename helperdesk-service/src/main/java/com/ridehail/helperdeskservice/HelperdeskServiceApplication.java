package com.ridehail.helperdeskservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.ridehail")
public class HelperdeskServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HelperdeskServiceApplication.class, args);
    }
}
