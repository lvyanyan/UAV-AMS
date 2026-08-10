package com.uav.airspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uav.airspace")
public class AirspaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirspaceApplication.class, args);
    }
}
