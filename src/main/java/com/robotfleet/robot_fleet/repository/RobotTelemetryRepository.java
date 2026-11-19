package com.robotfleet.robot_fleet.repository;

import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.robotfleet.robot_fleet.RobotTelemetry;

public interface RobotTelemetryRepository
        extends JpaRepository<RobotTelemetry, Long> {

    boolean existsByRobot_IdAndSessionIdAndSequenceNumber(
            UUID robotId,
            UUID sessionId,
            long sequenceNumber);

    List<RobotTelemetry>
    findByRobot_IdAndTimestampBetweenOrderByTimestampAsc(
            UUID robotId,
            Instant start,
            Instant end);

    Optional<RobotTelemetry>
    findTopByRobot_IdAndSessionIdOrderBySequenceNumberDesc(
            UUID robotId,
            UUID sessionId);
    
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


