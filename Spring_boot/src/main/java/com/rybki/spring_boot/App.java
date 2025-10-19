package com.rybki.spring_boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@ComponentScan(basePackages = "com.rybki.spring_boot")
@EnableScheduling
public class App {

    public static void main(final String[] args) {
        SpringApplication.run(App.class, args);
    }
}
