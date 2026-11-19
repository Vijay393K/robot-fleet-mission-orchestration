
package com.robotfleet.mission.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.robotfleet.mission.entity.Mission;

import jakarta.persistence.LockModeType;

public interface MissionRepository
        extends JpaRepository<Mission, UUID>,
                JpaSpecificationExecutor<Mission> {

    Page<Mission> findByAssignedRobotId(
            UUID robotId,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM Mission m
            WHERE m.id = :missionId
            """)
    Optional<Mission> findByIdForUpdate(
            @Param("missionId") UUID missionId
    );

    @Query(value = """
            SELECT m.*
            FROM missions m
            WHERE m.status = 'PENDING'
              AND m.assigned_robot_id IS NULL
              AND NOT EXISTS (
                  SELECT 1
                  FROM mission_assignment_operations o
                  WHERE o.mission_id = m.id
                    AND o.status IN (
                        'RESERVE_PENDING',
                        'CONFIRM_PENDING',
                        'CONFIRMED',
                        'RELEASE_PENDING'
                    )
              )
              AND (
                  CASE m.priority
                      WHEN 'CRITICAL' THEN 0
                      WHEN 'HIGH' THEN 1
                      WHEN 'MEDIUM' THEN 2
                      WHEN 'LOW' THEN 3
                      ELSE 4
                  END,
                  m.created_at,
                  m.id
              ) > (:rank, :createdAt, :lastMissionId)
            ORDER BY
                  CASE m.priority
                      WHEN 'CRITICAL' THEN 0
                      WHEN 'HIGH' THEN 1
                      WHEN 'MEDIUM' THEN 2
                      WHEN 'LOW' THEN 3
                      ELSE 4
                  END,
                  m.created_at,
                  m.id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<Mission> findSchedulableAfter(
            @Param("rank") int rank,
            @Param("createdAt") Instant createdAt,
            @Param("lastMissionId") UUID lastMissionId,
            @Param("batchSize") int batchSize
    );
}
