package com.uav.airspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = "com.uav.airspace",
    exclude = {DataSourceAutoConfiguration.class}
)
@EnableScheduling
public class AirspaceControllerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AirspaceControllerApplication.class, args);
        System.out.println("========================================");
        System.out.println("  UAV Airspace Controller started.      ");
        System.out.println("  Port: 8084                             ");
        System.out.println("========================================");
    }
}
