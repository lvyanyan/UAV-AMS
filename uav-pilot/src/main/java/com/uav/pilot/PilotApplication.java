package com.uav.pilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.uav.pilot")
@EnableScheduling
public class PilotApplication {
    public static void main(String[] args) {
        SpringApplication.run(PilotApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Pilot (驾驶员管理) started.       ");
        System.out.println("  Port: 8087                            ");
        System.out.println("========================================");
    }
}
