package com.uav.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Gateway (API网关) started.       ");
        System.out.println("  Port: 8080 → 统一入口                ");
        System.out.println("========================================");
    }
}
