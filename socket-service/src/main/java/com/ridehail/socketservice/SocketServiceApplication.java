package com.ridehail.socketservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.ridehail")
public class SocketServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SocketServiceApplication.class, args);
    }
}
