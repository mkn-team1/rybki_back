package com.rybki.spring_boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.rybki.spring_boot")
public class App {

    public static void main(final String[] args) {
        SpringApplication.run(App.class, args);
    }
}
