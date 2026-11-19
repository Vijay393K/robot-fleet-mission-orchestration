
package com.robotfleet.fleet.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.repository.RobotRepository;

@Service
public class RobotFailureRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(RobotFailureRecoveryService.class);

    private static final Duration HEARTBEAT_TIMEOUT =
            Duration.ofSeconds(30);

    private final RobotRepository robotRepository;

    public RobotFailureRecoveryService(
            RobotRepository robotRepository) {
        this.robotRepository = robotRepository;
    }

    @Transactional
    public int detectAndRecoverOfflineRobots() {

        Instant cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT);

        List<Robot> staleRobots =
                robotRepository.findStaleRobotsForRecovery(cutoff);

        for (Robot robot : staleRobots) {

            robot.setStatus(RobotStatus.OFFLINE);
            robot.setSafetyHold(true);

            log.warn(
                    "Robot marked OFFLINE: robotId={}, currentMissionId={}, lastHeartbeat={}",
                    robot.getId(),
                    robot.getCurrentMissionId(),
                    robot.getLastHeartbeat());
        }

        return staleRobots.size();
    }
}
