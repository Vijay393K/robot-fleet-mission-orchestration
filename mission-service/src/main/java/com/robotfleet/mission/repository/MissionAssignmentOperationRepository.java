
package com.robotfleet.mission.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;

import jakarta.persistence.LockModeType;

public interface MissionAssignmentOperationRepository
        extends JpaRepository<MissionAssignmentOperation, UUID> {

    Optional<MissionAssignmentOperation>
            findByMissionIdAndAssignmentGeneration(
                    UUID missionId,
                    long assignmentGeneration
            );

    Optional<MissionAssignmentOperation> findByReservationId(
            UUID reservationId
    );

    Page<MissionAssignmentOperation>
            findByMissionIdOrderByCreatedAtDescIdAsc(
                    UUID missionId,
                    Pageable pageable
            );

    boolean existsByMissionIdAndStatusIn(
            UUID missionId,
            Collection<AssignmentOperationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT operation
            FROM MissionAssignmentOperation operation
            WHERE operation.id = :operationId
            """)
    Optional<MissionAssignmentOperation> findByIdForUpdate(
            @Param("operationId") UUID operationId
    );

    // Claim pending operations using PostgreSQL row locks.
    // SKIP LOCKED prevents competing workers from
    // claiming the same operation simultaneously.
    @Query(value = """
            SELECT id
            FROM mission_assignment_operations
            WHERE status IN (
                'RESERVE_PENDING',
                'CONFIRM_PENDING',
                'RELEASE_PENDING'
            )
              AND next_attempt_at <= :now
              AND (
                  lease_expires_at IS NULL
                  OR lease_expires_at <= :now
              )
            ORDER BY next_attempt_at ASC, id ASC
            FOR UPDATE SKIP LOCKED
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findDueOperationIdsForUpdate(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize
    );

    // Find released reservations that still require
    // assignment-history finalization.
    //
    // Supports both:
    // 1. RECOVERY_PENDING -> PENDING
    // 2. COMPLETED -> COMPLETED
    //
    // Only matching, active assignments are returned.
    @Query(value = """
            SELECT o.id
            FROM mission_assignment_operations o
            JOIN missions m
              ON m.id = o.mission_id
            JOIN mission_assignments a
              ON a.reservation_id = o.reservation_id
            WHERE o.status = 'RELEASED'
              AND m.status IN (
                  'RECOVERY_PENDING',
                  'COMPLETED'
              )
              AND m.assigned_robot_id = o.robot_id
              AND m.assignment_generation =
                  o.assignment_generation
              AND a.mission_id = o.mission_id
              AND a.robot_id = o.robot_id
              AND a.assignment_generation =
                  o.assignment_generation
              AND a.ended_at IS NULL
              AND o.id > :afterId
            ORDER BY o.id ASC
            LIMIT :batchSize
            """, nativeQuery = true)
    List<UUID> findReleasedAssignmentsPendingFinalizationAfter(
            @Param("afterId") UUID afterId,
            @Param("batchSize") int batchSize
    );
}
