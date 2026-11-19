package com.robotfleet.mission.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "mission.scheduler.enabled=false"
)
@ActiveProfiles("test")
@Transactional
class MissionSchedulingRepositoryIntegrationTest {

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
    private MissionRepository missionRepository;

    @Autowired
    private MissionAssignmentOperationRepository operationRepository;

    @Test
    void shouldOrderMissionsByPriorityAcrossPages() {
        createMission(MissionPriority.LOW);
        createMission(MissionPriority.MEDIUM);
        createMission(MissionPriority.HIGH);
        createMission(MissionPriority.CRITICAL);

        List<Mission> firstPage = missionRepository.findSchedulableAfter(
                -1,
                Instant.EPOCH,
                new UUID(0L, 0L),
                2);

        assertEquals(
                List.of(MissionPriority.CRITICAL, MissionPriority.HIGH),
                firstPage.stream()
                        .map(Mission::getPriority)
                        .toList());

        Mission cursor = firstPage.getLast();

        List<Mission> secondPage = missionRepository.findSchedulableAfter(
                priorityRank(cursor.getPriority()),
                cursor.getCreatedAt(),
                cursor.getId(),
                2);

        assertEquals(
                List.of(MissionPriority.MEDIUM, MissionPriority.LOW),
                secondPage.stream()
                        .map(Mission::getPriority)
                        .toList());
    }

    @Test
    void shouldExcludeMissionsWithPendingAssignmentOperations() {
        Mission blockedMission = createMission(MissionPriority.CRITICAL);
        Mission eligibleMission = createMission(MissionPriority.HIGH);

        MissionAssignmentOperation operation =
                new MissionAssignmentOperation(
                        blockedMission.getId(),
                        UUID.randomUUID(),
                        1,
                        "SCHEDULER",
                        "Scheduling integration test",
                        null);

        operationRepository.saveAndFlush(operation);

        List<Mission> missions = missionRepository.findSchedulableAfter(
                -1,
                Instant.EPOCH,
                new UUID(0L, 0L),
                10);

        assertEquals(
                List.of(eligibleMission.getId()),
                missions.stream()
                        .map(Mission::getId)
                        .toList());
    }

    @Test
    void shouldExcludeMissionsThatAreNotPending() {
        Mission cancelledMission = createMission(MissionPriority.CRITICAL);
        cancelledMission.setStatus(MissionStatus.CANCELLED);
        missionRepository.saveAndFlush(cancelledMission);

        Mission pendingMission = createMission(MissionPriority.HIGH);

        List<Mission> missions = missionRepository.findSchedulableAfter(
                -1,
                Instant.EPOCH,
                new UUID(0L, 0L),
                10);

        assertEquals(
                List.of(pendingMission.getId()),
                missions.stream()
                        .map(Mission::getId)
                        .toList());
    }

    private Mission createMission(MissionPriority priority) {
        Mission mission = new Mission();

        mission.setMissionType(MissionType.DELIVERY);
        mission.setPriority(priority);
        mission.setRequiredRobotType(RobotType.CARRIER);
        mission.setPickupX(10);
        mission.setPickupY(20);
        mission.setDropX(30);
        mission.setDropY(40);
        mission.setStatus(MissionStatus.PENDING);

        return missionRepository.saveAndFlush(mission);
    }

    private int priorityRank(MissionPriority priority) {
        return switch (priority) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }
}