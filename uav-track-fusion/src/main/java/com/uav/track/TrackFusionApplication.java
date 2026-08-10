package com.uav.track;

import com.uav.track.kafka.FittedTrackProducer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
    scanBasePackages = "com.uav.track",
    exclude = {DataSourceAutoConfiguration.class}
)
@EnableKafka
@EnableScheduling
public class TrackFusionApplication {

    private final FittedTrackProducer trackProducer;

    public TrackFusionApplication(FittedTrackProducer trackProducer) {
        this.trackProducer = trackProducer;
    }

    public static void main(String[] args) {
        SpringApplication.run(TrackFusionApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        trackProducer.start();
        System.out.println("========================================");
        System.out.println("  UAV Track Fusion Service Started     ");
        System.out.println("  Port: 8089                           ");
        System.out.println("========================================");
    }
}
