
package com.robotfleet.fleet.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.robotfleet.fleet.service.RobotFailureRecoveryService;

@Component
public class RobotOfflineScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(RobotOfflineScheduler.class);

    private final RobotFailureRecoveryService recoveryService;

    public RobotOfflineScheduler(
            RobotFailureRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 10000)
    public void detectOfflineRobots() {

        int recoveredCount =
                recoveryService.detectAndRecoverOfflineRobots();

        if (recoveredCount > 0) {
            log.warn(
                    "Offline detection completed: {} robots marked OFFLINE",
                    recoveredCount);
        }
    }
}
