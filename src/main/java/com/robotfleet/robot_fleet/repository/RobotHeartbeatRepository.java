
package com.robotfleet.robot_fleet.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.robotfleet.robot_fleet.RobotHeartbeat;

public interface RobotHeartbeatRepository
        extends JpaRepository<RobotHeartbeat, Long> {

    List<RobotHeartbeat>
        findTop20ByRobot_IdOrderByReceivedAtDesc(UUID robotId);
}
