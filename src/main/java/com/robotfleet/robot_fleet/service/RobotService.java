
package com.robotfleet.robot_fleet.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.robotfleet.robot_fleet.Robot;
import com.robotfleet.robot_fleet.RobotStatus;
import com.robotfleet.robot_fleet.repository.RobotRepository;

@Service
public class RobotService {

    private static final int MINIMUM_BATTERY = 20;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    private final RobotRepository robotRepository;

    public RobotService(RobotRepository robotRepository) {
        this.robotRepository = robotRepository;
    }

    public boolean isRobotAvailable(Robot robot) {

        if (robot == null)
            return false;

        if (robot.getStatus() != RobotStatus.IDLE) {
            return false;
        }

        if (robot.getBatteryPercentage() < MINIMUM_BATTERY) {
            return false;
        }

        if (robot.getCurrentMissionId() != null) {
            return false;
        }

        if (robot.getLastHeartbeat() == null) {
            return false;
        }

        Duration elapsed = Duration.between(
                robot.getLastHeartbeat(), Instant.now());

        return !elapsed.isNegative()
                && elapsed.compareTo(
                        Duration.ofSeconds(HEARTBEAT_TIMEOUT_SECONDS)) <= 0;
    }

    public List<Robot> getInactiveRobots() {

        Instant cutoff = Instant.now()
        		.minusSeconds(HEARTBEAT_TIMEOUT_SECONDS);

        return robotRepository
                .findByLastHeartbeatIsNullOrLastHeartbeatBefore(cutoff);    }
}
