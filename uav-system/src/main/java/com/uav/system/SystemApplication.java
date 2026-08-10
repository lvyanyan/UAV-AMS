package com.uav.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV System (RBAC) started.            ");
        System.out.println("  Port: 8081                            ");
        System.out.println("========================================");
    }
}
