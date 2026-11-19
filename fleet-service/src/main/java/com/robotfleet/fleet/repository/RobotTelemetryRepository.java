
package com.robotfleet.fleet.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.robotfleet.fleet.entity.RobotTelemetry;

public interface RobotTelemetryRepository
        extends JpaRepository<RobotTelemetry, Long> {

    Optional<RobotTelemetry>
        findByRobot_IdAndSessionIdAndSequenceNumber(
                UUID robotId,
                UUID sessionId,
                long sequenceNumber);

    Optional<RobotTelemetry>
        findTopByRobot_IdAndSessionIdOrderBySequenceNumberDesc(
                UUID robotId,
                UUID sessionId);

    Page<RobotTelemetry>
        findByRobot_IdAndTimestampBetween(
                UUID robotId,
                Instant start,
                Instant end,
                Pageable pageable);

    
    @Query(value = """
            SELECT latest.*
            FROM robots r
            CROSS JOIN LATERAL (
                SELECT rt.*
                FROM robot_telemetry rt
                WHERE rt.robot_id = r.id
                ORDER BY
                    rt.telemetry_timestamp DESC,
                    rt.id DESC
                LIMIT 1
            ) latest
            ORDER BY latest.robot_id
            """, nativeQuery = true)
    List<RobotTelemetry> findLatestTelemetryForEachRobot();
}
