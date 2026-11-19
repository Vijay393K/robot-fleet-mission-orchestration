package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.robotfleet.mission.MissionServiceApplication;
import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse.RobotSummary;
import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionAssignmentRepository;
import com.robotfleet.mission.repository.MissionRepository;

@Testcontainers
class MissionSchedulerRestartIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void recoversConfirmedReservationAfterMissionServiceRestart() {
        String schema = newTestSchema();
        ConfigurableApplicationContext beforeRestart = null;
        ConfigurableApplicationContext afterRestart = null;

        try {
            beforeRestart = startMissionService(schema);

            Mission mission = createMission();
            mission.setAssignmentGeneration(1);

            Mission savedMission = beforeRestart
                    .getBean(MissionRepository.class)
                    .saveAndFlush(mission);

            UUID missionId = savedMission.getId();
            UUID robotId = UUID.randomUUID();
            UUID reservationId = UUID.randomUUID();

            MissionAssignmentOperation operation =
                    new MissionAssignmentOperation(
                            missionId,
                            robotId,
                            1,
                            "SCHEDULER",
                            "Restart recovery integration test",
                            "restart-recovery-" + UUID.randomUUID());

            operation.reservationSucceeded(
                    reservationId,
                    Instant.now().plusSeconds(120));

            MissionAssignmentOperation savedOperation = beforeRestart
                    .getBean(MissionAssignmentOperationRepository.class)
                    .saveAndFlush(operation);

            UUID operationId = savedOperation.getId();

            beforeRestart.close();
            beforeRestart = null;

            afterRestart = startMissionService(schema);

            FleetServiceClient fleetClient =
                    afterRestart.getBean(FleetServiceClient.class);

            Instant now = Instant.now();
            FleetRobotReservationResponse confirmedReservation =
                    new FleetRobotReservationResponse(
                            reservationId,
                            robotId,
                            missionId,
                            FleetRobotReservationResponse.ReservationStatus
                                    .CONFIRMED,
                            now.minusSeconds(10),
                            now.plusSeconds(120),
                            now,
                            null);

            when(fleetClient.confirmReservation(reservationId))
                    .thenReturn(confirmedReservation);

            MissionAssignmentLeaseService leaseService =
                    afterRestart.getBean(MissionAssignmentLeaseService.class);

            MissionAssignmentLeaseService.OperationClaim claim =
                    leaseService.claimDueOperations(100).stream()
                            .filter(candidate ->
                                    candidate.operationId().equals(operationId))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError(
                                    "Pending confirmation was not recovered "
                                            + "after restart"));

            assertEquals(
                    AssignmentOperationStatus.CONFIRM_PENDING,
                    claim.status());

            afterRestart.getBean(MissionConfirmationProcessor.class)
                    .processConfirm(claim);

            Mission recoveredMission = afterRestart
                    .getBean(MissionRepository.class)
                    .findById(missionId)
                    .orElseThrow();

            MissionAssignmentOperation recoveredOperation = afterRestart
                    .getBean(MissionAssignmentOperationRepository.class)
                    .findById(operationId)
                    .orElseThrow();

            assertEquals(MissionStatus.ASSIGNED, recoveredMission.getStatus());
            assertEquals(robotId, recoveredMission.getAssignedRobotId());
            assertEquals(
                    AssignmentOperationStatus.CONFIRMED,
                    recoveredOperation.getStatus());

            var assignment = afterRestart
                    .getBean(MissionAssignmentRepository.class)
                    .findByReservationId(reservationId)
                    .orElseThrow();

            assertEquals(missionId, assignment.getMissionId());
            assertEquals(robotId, assignment.getRobotId());
            assertEquals(1, assignment.getAssignmentGeneration());
        } finally {
            close(afterRestart);
            close(beforeRestart);
        }
    }

    @Test
    void resumesSchedulingAfterRestartWithoutCreatingDuplicateIntents() {
        String schema = newTestSchema();
        ConfigurableApplicationContext beforeRestart = null;
        ConfigurableApplicationContext afterRestart = null;

        try {
            beforeRestart = startMissionService(schema);

            Mission savedMission = beforeRestart
                    .getBean(MissionRepository.class)
                    .saveAndFlush(createMission());

            UUID missionId = savedMission.getId();

            beforeRestart.close();
            beforeRestart = null;

            afterRestart = startMissionService(schema);

            MissionRepository missionRepository =
                    afterRestart.getBean(MissionRepository.class);

            Mission pendingMission =
                    missionRepository.findById(missionId).orElseThrow();

            assertEquals(MissionStatus.PENDING, pendingMission.getStatus());
            assertNull(pendingMission.getAssignedRobotId());

            UUID robotId = UUID.randomUUID();

            RobotSummary robot = new RobotSummary(
                    robotId,
                    "Restart-Recovery-Robot",
                    RobotType.CARRIER,
                    "IDLE",
                    90,
                    10.0,
                    20.0,
                    Instant.now(),
                    null,
                    false);

            FleetRobotAvailabilityResponse availability =
                    new FleetRobotAvailabilityResponse(
                            robotId,
                            RobotType.CARRIER,
                            true,
                            90,
                            new FleetRobotAvailabilityResponse.Location(
                                    10.0, 20.0),
                            List.of());

            FleetServiceClient fleetClient =
                    afterRestart.getBean(FleetServiceClient.class);

            when(fleetClient.getIdleRobots(
                    RobotType.CARRIER, 25, 0, 100))
                    .thenReturn(new FleetRobotPageResponse(
                            List.of(robot), 0, 100, 1, 1));

            when(fleetClient.getRobotAvailability(robotId))
                    .thenReturn(availability);

            MissionSchedulingService scheduler =
                    afterRestart.getBean(MissionSchedulingService.class);

            MissionSchedulingService.SchedulingResult firstRun =
                    scheduler.executeScheduling();

            assertEquals(1, firstRun.intentsCreated());
            assertEquals(1, firstRun.assignments().size());

            MissionSchedulingService.SchedulingResult retryRun =
                    scheduler.executeScheduling();

            assertEquals(0, retryRun.intentsCreated());
            assertEquals(0, retryRun.assignments().size());

            List<MissionAssignmentOperation> persistedOperations =
                    afterRestart
                            .getBean(MissionAssignmentOperationRepository.class)
                            .findAll()
                            .stream()
                            .filter(operation ->
                                    operation.getMissionId().equals(missionId))
                            .toList();

            assertEquals(1, persistedOperations.size());
            assertEquals(
                    AssignmentOperationStatus.RESERVE_PENDING,
                    persistedOperations.getFirst().getStatus());

            Mission stillPending =
                    missionRepository.findById(missionId).orElseThrow();

            assertEquals(MissionStatus.PENDING, stillPending.getStatus());
            assertNull(stillPending.getAssignedRobotId());
        } finally {
            close(afterRestart);
            close(beforeRestart);
        }
    }

    private ConfigurableApplicationContext startMissionService(String schema) {
        String jdbcUrl = postgres.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        String url = jdbcUrl + separator + "currentSchema=" + schema;

        String[] arguments = {
                "--spring.profiles.active=test,scheduler-concurrency",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.flyway.default-schema=" + schema,
                "--spring.flyway.schemas=" + schema,
                "--spring.jpa.properties.hibernate.default_schema=" + schema,
                "--spring.main.web-application-type=none",
                "--mission.scheduler.enabled=false",
                "--mission.scheduler.max-assignments-per-run=1",
                "--mission.assignment.recovery.initial-delay-ms=3600000",
                "--mission.offline.initial-delay-ms=3600000",
                "--mission.recovery.finalization.initial-delay-ms=3600000"
        };

        return new SpringApplicationBuilder(
                MissionServiceApplication.class,
                TestFleetClientConfiguration.class)
                .web(WebApplicationType.NONE)
                .run(arguments);
    }

    private String newTestSchema() {
        return "scheduler_restart_"
                + UUID.randomUUID().toString().replace("-", "");
    }

    private Mission createMission() {
        Mission mission = new Mission();
        mission.setMissionType(MissionType.DELIVERY);
        mission.setPriority(MissionPriority.HIGH);
        mission.setRequiredRobotType(RobotType.CARRIER);
        mission.setPickupX(10.0);
        mission.setPickupY(20.0);
        mission.setDropX(50.0);
        mission.setDropY(60.0);
        return mission;
    }

    private void close(ConfigurableApplicationContext context) {
        if (context != null) {
            context.close();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestFleetClientConfiguration {

        @Bean
        @Primary
        FleetServiceClient testFleetServiceClient() {
            return mock(FleetServiceClient.class);
        }
    }
}