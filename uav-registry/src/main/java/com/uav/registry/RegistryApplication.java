package com.uav.registry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.uav.registry")
@EnableScheduling
public class RegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(RegistryApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Registry (实名登记) started.      ");
        System.out.println("  Port: 8086                            ");
        System.out.println("========================================");
    }
}
