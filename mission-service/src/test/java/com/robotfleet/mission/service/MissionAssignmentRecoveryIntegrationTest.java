package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignment;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionAssignmentRepository;
import com.robotfleet.mission.repository.MissionRepository;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class MissionAssignmentRecoveryIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MissionReservationProcessor reservationProcessor;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MissionReleaseProcessor releaseProcessor;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private MissionAssignmentOutcomeService outcomeService;

    @Autowired
    private MissionAssignmentOperationRepository operationRepository;

    @Autowired
    private MissionAssignmentRepository assignmentRepository;

    @Autowired
    private MissionAssignmentLeaseService leaseService;

    @Autowired
    private MissionConfirmationProcessor confirmationProcessor;

    @Autowired
    private MissionAssignmentFinalizationService finalizationService;

    @MockitoBean
    private FleetServiceClient fleetServiceClient;

    @Test
    void assignmentInsertFailureRollsBackMissionUpdate() {
        Mission existingMission = createMission();
        Mission targetMission = createMission();

        UUID robotId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(300);

        MissionAssignment existingAssignment =
                assignmentRepository.saveAndFlush(
                        new MissionAssignment(
                                existingMission.getId(),
                                UUID.randomUUID(),
                                reservationId,
                                1L,
                                "ROLLBACK_TEST"));

        MissionAssignmentOperation operation =
                new MissionAssignmentOperation(
                        targetMission.getId(),
                        robotId,
                        1L,
                        "SCHEDULER",
                        "Verify assignment rollback",
                        "rollback-test");

        operation.reservationSucceeded(reservationId, expiresAt);
        operation.claimLease(leaseToken, expiresAt);
        operation = operationRepository.saveAndFlush(operation);

        try {
            OperationClaim claim = new OperationClaim(
                    operation.getId(),
                    targetMission.getId(),
                    robotId,
                    1L,
                    AssignmentOperationStatus.CONFIRM_PENDING,
                    reservationId,
                    expiresAt,
                    leaseToken,
                    operation.getAttemptCount());

            FleetRobotReservationResponse confirmed =
                    new FleetRobotReservationResponse(
                            reservationId,
                            robotId,
                            targetMission.getId(),
                            FleetRobotReservationResponse.ReservationStatus.CONFIRMED,
                            now,
                            expiresAt,
                            now,
                            null);

            DataIntegrityViolationException error = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> finalizationService.finalizeConfirmedReservation(
                            claim, confirmed));

            assertTrue(error.getMostSpecificCause()
                    .getMessage()
                    .contains("uq_mission_assignments_reservation"));

            Mission reloadedMission = missionRepository
                    .findById(targetMission.getId())
                    .orElseThrow();

            assertEquals(MissionStatus.PENDING, reloadedMission.getStatus());
            assertNull(reloadedMission.getAssignedRobotId());

            assertEquals(
                    existingMission.getId(),
                    assignmentRepository.findByReservationId(reservationId)
                            .orElseThrow()
                            .getMissionId());

            assertEquals(
                    AssignmentOperationStatus.CONFIRM_PENDING,
                    operationRepository.findById(operation.getId())
                            .orElseThrow()
                            .getStatus());
        } finally {
            operationRepository.deleteById(operation.getId());
            assignmentRepository.delete(existingAssignment);
            missionRepository.deleteById(targetMission.getId());
            missionRepository.deleteById(existingMission.getId());
        }
    }

    @Test
    void shouldRecoverCommittedConfirmationOperationAndFinalizeOnce() {
        Mission mission = createMission();
        UUID robotId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        MissionAssignmentOperation operation =
                new MissionAssignmentOperation(
                        mission.getId(),
                        robotId,
                        1,
                        "SCHEDULER",
                        "Recovery integration test",
                        "recovery-test-" + UUID.randomUUID());

        operation.reservationSucceeded(
                reservationId,
                Instant.now().plusSeconds(120));

        operationRepository.saveAndFlush(operation);

        List<OperationClaim> claims =
                leaseService.claimDueOperations(100);

        OperationClaim claim = claims.stream()
                .filter(candidate ->
                        candidate.operationId().equals(operation.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Committed operation was not claimed for recovery"));

        assertEquals(AssignmentOperationStatus.CONFIRM_PENDING, claim.status());
        assertEquals(reservationId, claim.reservationId());

        FleetRobotReservationResponse confirmed =
                new FleetRobotReservationResponse(
                        reservationId,
                        robotId,
                        mission.getId(),
                        FleetRobotReservationResponse.ReservationStatus.CONFIRMED,
                        Instant.now().minusSeconds(10),
                        Instant.now().plusSeconds(120),
                        Instant.now(),
                        null);

        when(fleetServiceClient.confirmReservation(reservationId))
                .thenReturn(confirmed);

        confirmationProcessor.processConfirm(claim);

        Mission assignedMission = missionRepository
                .findById(mission.getId())
                .orElseThrow();

        assertEquals(MissionStatus.ASSIGNED, assignedMission.getStatus());
        assertEquals(robotId, assignedMission.getAssignedRobotId());
        assertEquals(1L, assignedMission.getAssignmentGeneration());

        MissionAssignment assignment = assignmentRepository
                .findByReservationId(reservationId)
                .orElseThrow();

        assertEquals(mission.getId(), assignment.getMissionId());
        assertEquals(robotId, assignment.getRobotId());
        assertEquals(1L, assignment.getAssignmentGeneration());
        assertNull(assignment.getEndedAt());

        MissionAssignmentOperation recoveredOperation =
                operationRepository.findById(operation.getId())
                        .orElseThrow();

        assertEquals(
                AssignmentOperationStatus.CONFIRMED,
                recoveredOperation.getStatus());

        assertTrue(leaseService.claimDueOperations(100).stream()
                .noneMatch(candidate ->
                        candidate.operationId().equals(operation.getId())));

        verify(fleetServiceClient).confirmReservation(reservationId);
        assertEquals(reservationId, assignment.getReservationId());
    }

    @Test
    void shouldReleaseReservationWhenMissionCannotBeFinalized() {
        Mission mission = createMission();
        UUID robotId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        MissionAssignmentOperation operation =
                new MissionAssignmentOperation(
                        mission.getId(),
                        robotId,
                        1,
                        "SCHEDULER",
                        "Compensation integration test",
                        "compensation-test-" + UUID.randomUUID());

        operation.reservationSucceeded(
                reservationId,
                Instant.now().plusSeconds(120));

        operationRepository.saveAndFlush(operation);

        OperationClaim confirmationClaim =
                leaseService.claimDueOperations(100).stream()
                        .filter(candidate ->
                                candidate.operationId().equals(operation.getId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "Confirmation operation was not claimed"));

        mission.setStatus(MissionStatus.CANCELLED);
        missionRepository.saveAndFlush(mission);

        FleetRobotReservationResponse confirmed =
                new FleetRobotReservationResponse(
                        reservationId,
                        robotId,
                        mission.getId(),
                        FleetRobotReservationResponse.ReservationStatus.CONFIRMED,
                        Instant.now().minusSeconds(10),
                        Instant.now().plusSeconds(120),
                        Instant.now(),
                        null);

        when(fleetServiceClient.confirmReservation(reservationId))
                .thenReturn(confirmed);

        confirmationProcessor.processConfirm(confirmationClaim);

        MissionAssignmentOperation releasePendingOperation =
                operationRepository.findById(operation.getId())
                        .orElseThrow();

        assertEquals(
                AssignmentOperationStatus.RELEASE_PENDING,
                releasePendingOperation.getStatus());

        assertTrue(assignmentRepository
                .findByReservationId(reservationId)
                .isEmpty());

        OperationClaim releaseClaim =
                leaseService.claimDueOperations(100).stream()
                        .filter(candidate ->
                                candidate.operationId().equals(operation.getId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "Compensation operation was not claimed"));

        FleetRobotReservationResponse released =
                new FleetRobotReservationResponse(
                        reservationId,
                        robotId,
                        mission.getId(),
                        FleetRobotReservationResponse.ReservationStatus.RELEASED,
                        confirmed.reservedAt(),
                        confirmed.expiresAt(),
                        confirmed.confirmedAt(),
                        Instant.now());

        when(fleetServiceClient.releaseReservation(reservationId))
                .thenReturn(released);

        releaseProcessor.processRelease(releaseClaim);

        MissionAssignmentOperation releasedOperation =
                operationRepository.findById(operation.getId())
                        .orElseThrow();

        assertEquals(
                AssignmentOperationStatus.RELEASED,
                releasedOperation.getStatus());

        assertNotNull(releasedOperation.getFinishedAt());

        assertEquals(
                MissionStatus.CANCELLED,
                missionRepository.findById(mission.getId())
                        .orElseThrow()
                        .getStatus());

        assertTrue(assignmentRepository
                .findByReservationId(reservationId)
                .isEmpty());

        verify(fleetServiceClient).confirmReservation(reservationId);
        verify(fleetServiceClient).releaseReservation(reservationId);
    }

    @Test
    void shouldAllowOnlyOneConcurrentWorkerToClaimAnOperation()
            throws Exception {

        Mission mission = createMission();

        MissionAssignmentOperation operation =
                new MissionAssignmentOperation(
                        mission.getId(),
                        UUID.randomUUID(),
                        1,
                        "SCHEDULER",
                        "Concurrent claim integration test",
                        "concurrent-claim-" + UUID.randomUUID());

        operationRepository.saveAndFlush(operation);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<List<OperationClaim>> claimTask = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting to start concurrent claim");
            }

            return leaseService.claimDueOperations(100);
        };

        try {
            Future<List<OperationClaim>> firstWorker =
                    executor.submit(claimTask);

            Future<List<OperationClaim>> secondWorker =
                    executor.submit(claimTask);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<List<OperationClaim>> results = List.of(
                    firstWorker.get(15, TimeUnit.SECONDS),
                    secondWorker.get(15, TimeUnit.SECONDS));

            List<OperationClaim> claimsForOperation = results.stream()
                    .flatMap(List::stream)
                    .filter(claim ->
                            claim.operationId().equals(operation.getId()))
                    .toList();

            assertEquals(1, claimsForOperation.size());

            assertTrue(outcomeService.abortRejectedReserve(
                    claimsForOperation.getFirst()));

            assertEquals(
                    AssignmentOperationStatus.ABORTED,
                    operationRepository.findById(operation.getId())
                            .orElseThrow()
                            .getStatus());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @Transactional
    void reclaimsExpiredLeaseAndRecoversOriginalFleetReservation() {
        Mission mission = createMission();
        UUID missionId = mission.getId();
        UUID robotId = UUID.randomUUID();
        UUID originalReservationId = UUID.randomUUID();
        UUID expiredLeaseToken = UUID.randomUUID();

        Instant now = Instant.now();
        Instant firstAttemptAt = now.minusSeconds(31);

        MissionAssignmentOperation operation =
                new MissionAssignmentOperation(
                        missionId,
                        robotId,
                        mission.getAssignmentGeneration(),
                        "SCHEDULER",
                        "Recover reservation after worker interruption",
                        "reservation-recovery-test");

        operation.claimLease(expiredLeaseToken, now.minusSeconds(1));
        operation.recordAttempt(firstAttemptAt);

        UUID operationId =
                operationRepository.saveAndFlush(operation).getId();

        String originalIdempotencyKey = operationId.toString();

        entityManager.clear();

        FleetRobotAvailabilityResponse reservedRobot =
                new FleetRobotAvailabilityResponse(
                        robotId,
                        RobotType.CARRIER,
                        false,
                        80,
                        new FleetRobotAvailabilityResponse.Location(10, 20),
                        List.of("Robot already reserved"));

        FleetRobotReservationResponse originalReservation =
                new FleetRobotReservationResponse(
                        originalReservationId,
                        robotId,
                        missionId,
                        FleetRobotReservationResponse.ReservationStatus.RESERVED,
                        firstAttemptAt,
                        now.plusSeconds(120),
                        null,
                        null);

        when(fleetServiceClient.getRobotAvailability(robotId))
                .thenReturn(reservedRobot);

        when(fleetServiceClient.reserveRobot(
                robotId, missionId, originalIdempotencyKey))
                .thenReturn(originalReservation);

        OperationClaim retryClaim =
                leaseService.claimDueOperations(100).stream()
                        .filter(claim ->
                                operationId.equals(claim.operationId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "Expected the worker to reclaim the expired "
                                        + "reservation operation"));

        assertEquals(2L, retryClaim.attemptCount());
        assertNotEquals(expiredLeaseToken, retryClaim.leaseToken());
        assertNull(retryClaim.reservationId());

        reservationProcessor.processReserve(retryClaim);

        verify(fleetServiceClient).reserveRobot(
                robotId, missionId, originalIdempotencyKey);

        entityManager.flush();
        entityManager.clear();

        MissionAssignmentOperation recoveredOperation =
                operationRepository.findById(operationId)
                        .orElseThrow();

        Mission pendingMission =
                missionRepository.findById(missionId)
                        .orElseThrow();

        assertEquals(
                originalReservationId,
                recoveredOperation.getReservationId());

        assertEquals(
                AssignmentOperationStatus.CONFIRM_PENDING,
                recoveredOperation.getStatus());

        assertEquals(MissionStatus.PENDING, pendingMission.getStatus());
        assertNull(pendingMission.getAssignedRobotId());

        assertTrue(assignmentRepository
                .findByReservationId(originalReservationId)
                .isEmpty());
    }

    private Mission createMission() {
        Mission mission = new Mission();
        mission.setMissionType(MissionType.DELIVERY);
        mission.setPriority(MissionPriority.HIGH);
        mission.setRequiredRobotType(RobotType.CARRIER);
        mission.setPickupX(10);
        mission.setPickupY(20);
        mission.setDropX(30);
        mission.setDropY(40);
        mission.setStatus(MissionStatus.PENDING);
        mission.setAssignmentGeneration(1);

        return missionRepository.saveAndFlush(mission);
    }
}