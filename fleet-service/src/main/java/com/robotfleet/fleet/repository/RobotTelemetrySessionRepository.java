
package com.robotfleet.fleet.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.robotfleet.fleet.entity.RobotTelemetrySession;

public interface RobotTelemetrySessionRepository
        extends JpaRepository<RobotTelemetrySession, UUID> {

    Optional<RobotTelemetrySession> findByRobot_IdAndEndedAtIsNull(
            UUID robotId);

    Optional<RobotTelemetrySession> findBySessionIdAndRobot_Id(
            UUID sessionId,
            UUID robotId);
}
