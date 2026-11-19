package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.robotfleet.mission.controller.MissionController;
import com.robotfleet.mission.dto.MissionRequest;
import com.robotfleet.mission.dto.MissionResponse;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.enums.RobotType;
import com.robotfleet.mission.repository.MissionRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class MissionIdempotencyIntegrationTest {

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
    private MissionController missionController;

    @Autowired
    private MissionRepository missionRepository;

    @Test
    void duplicateMissionRequestCreatesOnlyOneMission() {
        long missionsBefore = missionRepository.count();
        String idempotencyKey = "duplicate-mission-" + UUID.randomUUID();

        MissionRequest originalRequest = createRequest(MissionPriority.HIGH);

        var firstResponse =
                missionController.createMission(originalRequest, idempotencyKey);
        var retryResponse =
                missionController.createMission(originalRequest, idempotencyKey);

        assertEquals(HttpStatus.CREATED, firstResponse.getStatusCode());
        assertEquals(HttpStatus.CREATED, retryResponse.getStatusCode());

        MissionResponse firstMission = firstResponse.getBody();
        MissionResponse retriedMission = retryResponse.getBody();

        assertNotNull(firstMission);
        assertNotNull(retriedMission);
        assertEquals(firstMission.id(), retriedMission.id());
        assertEquals(missionsBefore + 1, missionRepository.count());

        MissionRequest differentRequest = createRequest(MissionPriority.LOW);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> missionController.createMission(
                        differentRequest,
                        idempotencyKey));

        assertEquals(HttpStatus.CONFLICT.value(), exception.getStatusCode().value());
    }

    private MissionRequest createRequest(MissionPriority priority) {
        return new MissionRequest(
                MissionType.PICKUP,
                priority,
                RobotType.PICKER,
                new MissionRequest.Location(10.0, 20.0),
                new MissionRequest.Location(50.0, 60.0));
    }
}