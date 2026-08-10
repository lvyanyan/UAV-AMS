package com.uav.flightplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.uav.flightplan")
public class FlightPlanApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlightPlanApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Flight Plan (飞行计划) started.   ");
        System.out.println("  Port: 8088                            ");
        System.out.println("========================================");
    }
}
