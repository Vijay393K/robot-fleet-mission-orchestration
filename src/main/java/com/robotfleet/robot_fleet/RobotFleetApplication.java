package com.robotfleet.robot_fleet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RobotFleetApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotFleetApplication.class, args);
        
    }
}