package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;

import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.robotfleet.mission.MissionServiceApplication;
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
class MissionSchedulerTwoInstancesIntegrationTest {

    private static final String TEST_SCHEMA =
            "scheduler_two_instances_test";

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void twoApplicationInstancesCannotClaimTheSameRobot()
            throws Exception {

        ConfigurableApplicationContext firstInstance = null;
        ConfigurableApplicationContext secondInstance = null;
        List<UUID> missionIds = new ArrayList<>();

        try {
            firstInstance = startMissionServiceInstance();
            secondInstance = startMissionServiceInstance();

            MissionRepository missionRepository =
                    firstInstance.getBean(MissionRepository.class);

            Mission firstMission = createMission();
            Mission secondMission = createMission();

            missionIds.addAll(
                    missionRepository.saveAllAndFlush(
                                    List.of(firstMission, secondMission))
                            .stream()
                            .map(Mission::getId)
                            .toList());

            UUID robotId = UUID.randomUUID();

            RobotSummary robot = new RobotSummary(
                    robotId,
                    "Two-Instance-Concurrency-Robot",
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

            configureFleetClient(
                    firstInstance.getBean(FleetServiceClient.class),
                    robot,
                    availability,
                    robotId);

            configureFleetClient(
                    secondInstance.getBean(FleetServiceClient.class),
                    robot,
                    availability,
                    robotId);

            MissionSchedulingService firstScheduler =
                    firstInstance.getBean(MissionSchedulingService.class);

            MissionSchedulingService secondScheduler =
                    secondInstance.getBean(MissionSchedulingService.class);

            CyclicBarrier startTogether = new CyclicBarrier(2);

            try (ExecutorService workers =
                    Executors.newFixedThreadPool(2)) {

                Future<SchedulerRunOutcome> firstRun =
                        workers.submit(() ->
                                runAfterBarrier(firstScheduler, startTogether));

                Future<SchedulerRunOutcome> secondRun =
                        workers.submit(() ->
                                runAfterBarrier(secondScheduler, startTogether));

                SchedulerRunOutcome firstOutcome =
                        firstRun.get(30, TimeUnit.SECONDS);

                SchedulerRunOutcome secondOutcome =
                        secondRun.get(30, TimeUnit.SECONDS);

                assertExpectedOutcome(firstOutcome);
                assertExpectedOutcome(secondOutcome);

                int returnedIntentCount =
                        countReturnedIntents(firstOutcome)
                                + countReturnedIntents(secondOutcome);

                assertEquals(
                        1,
                        returnedIntentCount,
                        "Exactly one application instance should create "
                                + "an assignment intent for the robot.");

                MissionAssignmentOperationRepository operations =
                        firstInstance.getBean(
                                MissionAssignmentOperationRepository.class);

                List<MissionAssignmentOperation> persistedOperations =
                        operations.findAll().stream()
                                .filter(operation ->
                                        missionIds.contains(
                                                operation.getMissionId()))
                                .toList();

                assertEquals(
                        1,
                        persistedOperations.size(),
                        "The shared database must contain only one "
                                + "assignment operation for this test.");

                assertEquals(
                        1,
                        persistedOperations.stream()
                                .map(MissionAssignmentOperation::getMissionId)
                                .distinct()
                                .count(),
                        "Only one mission may claim the shared robot.");
            }
        } finally {
            if (firstInstance != null && !missionIds.isEmpty()) {
                MissionAssignmentOperationRepository operations =
                        firstInstance.getBean(
                                MissionAssignmentOperationRepository.class);

                List<UUID> operationIds = operations.findAll().stream()
                        .filter(operation ->
                                missionIds.contains(operation.getMissionId()))
                        .map(MissionAssignmentOperation::getId)
                        .toList();

                operations.deleteAllById(operationIds);

                firstInstance.getBean(MissionRepository.class)
                        .deleteAllById(missionIds);
            }

            if (secondInstance != null) {
                secondInstance.close();
            }

            if (firstInstance != null) {
                firstInstance.close();
            }
        }
    }

    private ConfigurableApplicationContext startMissionServiceInstance() {
        String jdbcUrl = postgres.getJdbcUrl();
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        String url = jdbcUrl + separator + "currentSchema=" + TEST_SCHEMA;

        String[] arguments = {
                "--spring.profiles.active=test,scheduler-concurrency",
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.flyway.default-schema=" + TEST_SCHEMA,
                "--spring.flyway.schemas=" + TEST_SCHEMA,
                "--spring.jpa.properties.hibernate.default_schema="
                        + TEST_SCHEMA,
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

    private void configureFleetClient(
            FleetServiceClient client,
            RobotSummary robot,
            FleetRobotAvailabilityResponse availability,
            UUID robotId) {

        when(client.getIdleRobots(RobotType.CARRIER, 25, 0, 100))
                .thenReturn(new FleetRobotPageResponse(
                        List.of(robot), 0, 100, 1, 1));

        when(client.getRobotAvailability(robotId))
                .thenReturn(availability);
    }

    private SchedulerRunOutcome runAfterBarrier(
            MissionSchedulingService scheduler,
            CyclicBarrier startTogether) {

        try {
            startTogether.await(10, TimeUnit.SECONDS);

            return new SchedulerRunOutcome(
                    scheduler.executeScheduling(),
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

    @TestConfiguration(proxyBeanMethods = false)
    static class TestFleetClientConfiguration {

        @Bean
        @Primary
        FleetServiceClient testFleetServiceClient() {
            return mock(FleetServiceClient.class);
        }
    }
}