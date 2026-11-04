package com.robotfleet.robot_fleet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.robotfleet.robot_fleet.service.RobotFailureRecoveryService;

@Component
public class RobotOfflineScheduler {

    private final RobotFailureRecoveryService recoveryService;

    public RobotOfflineScheduler(
            RobotFailureRecoveryService recoveryService) {

        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedRate = 10000)
    public void checkRobotHeartbeats() {

        recoveryService.detectAndRecoverOfflineRobots();
    }
}