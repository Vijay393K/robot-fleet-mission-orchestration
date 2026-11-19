
package com.robotfleet.fleet.repository;

import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.robotfleet.fleet.entity.RobotReservation;
import com.robotfleet.fleet.enums.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface RobotReservationRepository
        extends JpaRepository<RobotReservation, UUID> {

    @EntityGraph(attributePaths = "robot")
    Optional<RobotReservation> findByOperationScopeAndIdempotencyKey(
            String operationScope,
            String idempotencyKey);

    List<RobotReservation> findByMissionIdAndStatusIn(
            UUID missionId,
            List<ReservationStatus> statuses);

    List<RobotReservation> findByRobot_IdAndStatusIn(
            UUID robotId,
            List<ReservationStatus> statuses);

    @EntityGraph(attributePaths = "robot")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM RobotReservation r
        WHERE r.id = :reservationId
        """)
    Optional<RobotReservation> findByIdForUpdate(
            @Param("reservationId") UUID reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM RobotReservation r
        WHERE r.status = :status
          AND r.expiresAt <= :currentTime
        ORDER BY r.expiresAt ASC
        """)
    List<RobotReservation> findExpiredReservationsForUpdate(
            @Param("status") ReservationStatus status,
            @Param("currentTime") Instant currentTime);
    
    

@Query("""
    SELECT reservation.robot.id
    FROM RobotReservation reservation
    WHERE reservation.id = :reservationId
    """)
Optional<UUID> findRobotIdByReservationId(
        @Param("reservationId") UUID reservationId);

}
