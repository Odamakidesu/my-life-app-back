package com.mylifeapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MylifeappApplication {

    public static void main(String[] args) {
        SpringApplication.run(MylifeappApplication.class, args);
    }

}
