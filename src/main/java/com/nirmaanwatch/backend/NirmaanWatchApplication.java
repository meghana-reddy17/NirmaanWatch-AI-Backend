package com.nirmaanwatch.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NirmaanWatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(NirmaanWatchApplication.class, args);
    }
}
