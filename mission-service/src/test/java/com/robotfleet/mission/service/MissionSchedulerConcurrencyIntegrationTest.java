package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse.RobotSummary;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "scheduler-concurrency"})
class MissionSchedulerConcurrencyIntegrationTest {

    private static final String TEST_SCHEMA = "scheduler_concurrency_test";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        String jdbcUrl = postgres.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";

        registry.add(
                "spring.datasource.url",
                () -> jdbcUrl + separator + "currentSchema=" + TEST_SCHEMA);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add(
                "spring.jpa.properties.hibernate.default_schema",
                () -> TEST_SCHEMA);
    }

    @Autowired
    private MissionSchedulingService schedulingService;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private MissionAssignmentOperationRepository operationRepository;

    @MockitoBean
    private FleetServiceClient fleetServiceClient;

    @Test
    void concurrentSchedulersCreateAtMostOneActiveIntentForTheSameRobot()
            throws Exception {

        UUID robotId = UUID.randomUUID();

        Mission firstMission = createMission();
        Mission secondMission = createMission();

        List<Mission> savedMissions =
                missionRepository.saveAllAndFlush(
                        List.of(firstMission, secondMission));

        List<UUID> missionIds = savedMissions.stream()
                .map(Mission::getId)
                .toList();

        RobotSummary robot = new RobotSummary(
                robotId,
                "Concurrent-Scheduler-Robot",
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
                        new FleetRobotAvailabilityResponse.Location(10.0, 20.0),
                        List.of());

        when(fleetServiceClient.getIdleRobots(
                RobotType.CARRIER, 25, 0, 100))
                .thenReturn(new FleetRobotPageResponse(
                        List.of(robot), 0, 100, 1, 1));

        when(fleetServiceClient.getRobotAvailability(robotId))
                .thenReturn(availability);

        CyclicBarrier startTogether = new CyclicBarrier(2);

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<SchedulerRunOutcome> firstRun =
                    workers.submit(() -> runAfterBarrier(startTogether));

            Future<SchedulerRunOutcome> secondRun =
                    workers.submit(() -> runAfterBarrier(startTogether));

            SchedulerRunOutcome firstOutcome =
                    firstRun.get(20, TimeUnit.SECONDS);

            SchedulerRunOutcome secondOutcome =
                    secondRun.get(20, TimeUnit.SECONDS);

            assertExpectedOutcome(firstOutcome);
            assertExpectedOutcome(secondOutcome);

            int returnedIntentCount =
                    countReturnedIntents(firstOutcome)
                            + countReturnedIntents(secondOutcome);

            assertTrue(
                    returnedIntentCount <= 1,
                    "Concurrent scheduler runs must not both claim the same robot.");

            List<MissionAssignmentOperation> persistedOperations =
                    operationRepository.findAll().stream()
                            .filter(operation ->
                                    missionIds.contains(operation.getMissionId()))
                            .toList();

            assertEquals(
                    1,
                    persistedOperations.size(),
                    "Only one assignment operation should be persisted "
                            + "for the shared robot.");

            assertEquals(
                    1,
                    persistedOperations.stream()
                            .map(MissionAssignmentOperation::getMissionId)
                            .distinct()
                            .count(),
                    "The persisted operation must belong to one mission.");
        } finally {
            List<UUID> operationIds = operationRepository.findAll().stream()
                    .filter(operation ->
                            missionIds.contains(operation.getMissionId()))
                    .map(MissionAssignmentOperation::getId)
                    .toList();

            operationRepository.deleteAllById(operationIds);
            missionRepository.deleteAllById(missionIds);
        }
    }

    private SchedulerRunOutcome runAfterBarrier(CyclicBarrier startTogether) {
        try {
            startTogether.await(10, TimeUnit.SECONDS);

            return new SchedulerRunOutcome(
                    schedulingService.executeScheduling(),
                    null);
        } catch (Exception exception) {
            return new SchedulerRunOutcome(null, exception);
        }
    }

    private void assertExpectedOutcome(SchedulerRunOutcome outcome) {
        if (outcome.error() != null) {
            assertTrue(
                    isDatabaseConstraintConflict(outcome.error()),
                    "Unexpected scheduler error: " + outcome.error());
        }
    }

    private boolean isDatabaseConstraintConflict(Throwable error) {
        Throwable current = error;

        while (current != null) {
            if (current instanceof DataIntegrityViolationException
                    || current instanceof ConstraintViolationException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private int countReturnedIntents(SchedulerRunOutcome outcome) {
        if (outcome.result() == null) {
            return 0;
        }

        return outcome.result().assignments().size();
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

    private record SchedulerRunOutcome(
            MissionSchedulingService.SchedulingResult result,
            Exception error) {
    }
}