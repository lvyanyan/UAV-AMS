package com.uav.alarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = "com.uav.alarm",
    exclude = {DataSourceAutoConfiguration.class}
)
@EnableScheduling
public class AlarmEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlarmEngineApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Alarm Engine started.            ");
        System.out.println("  Port: 8082                            ");
        System.out.println("========================================");
    }
}
