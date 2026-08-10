package com.uav.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(
    scanBasePackages = "com.uav.risk",
    exclude = {DataSourceAutoConfiguration.class}
)
public class RiskAssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskAssessmentApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Risk Assessment started.          ");
        System.out.println("  Port: 8083                             ");
        System.out.println("========================================");
    }
}
