package com.uav.alarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 告警持久化（alarm_record）启用数据源；端口见 application.yml（8095）
@SpringBootApplication
@EnableScheduling
public class AlarmEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlarmEngineApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Alarm Engine started.            ");
        System.out.println("========================================");
    }
}
