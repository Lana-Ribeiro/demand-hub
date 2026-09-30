package com.demandhub.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DemandHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemandHubApplication.class, args);
    }
}
