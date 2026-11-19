package com.robotfleet.fleet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.temporal.ChronoUnit;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.fleet.dto.TelemetryRequest;
import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.entity.RobotTelemetry;
import com.robotfleet.fleet.entity.RobotTelemetrySession;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;
import com.robotfleet.fleet.repository.RobotRepository;
import com.robotfleet.fleet.repository.RobotTelemetryRepository;
import com.robotfleet.fleet.repository.RobotTelemetrySessionRepository;

@ExtendWith(MockitoExtension.class)
class RobotTelemetryServiceTest {

    @Mock
    private RobotRepository robotRepository;

    @Mock
    private RobotTelemetryRepository telemetryRepository;

    @Mock
    private RobotTelemetrySessionRepository sessionRepository;

    @InjectMocks
    private RobotTelemetryService telemetryService;

    private UUID robotId;
    private UUID sessionId;
    private Robot robot;
    private RobotTelemetrySession session;

    @BeforeEach
    void setUp() {
        robotId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        robot = new Robot(
                robotId,
                "Telemetry-Test-Robot",
                RobotType.PICKER,
                80,
                10.0,
                20.0,
                RobotStatus.IDLE,
                Instant.now(),
                null,
                sessionId);

        session = new RobotTelemetrySession(
                sessionId,
                robot,
                Instant.now().minusSeconds(60));

        when(robotRepository.findByIdForUpdate(robotId))
                .thenReturn(Optional.of(robot));

        when(sessionRepository.findBySessionIdAndRobot_Id(sessionId, robotId))
                .thenReturn(Optional.of(session));
    }

    @Test
    void identicalDuplicateTelemetryIsRecognizedWithoutSavingAgain() {
        Instant timestamp = Instant.now().minusSeconds(5);
        TelemetryRequest request = request(5L, 80, 10.0, 20.0, timestamp);

        RobotTelemetry stored = telemetry(
                robot, sessionId, 5L, 80, 10.0, 20.0, timestamp);

        when(telemetryRepository
                .findByRobot_IdAndSessionIdAndSequenceNumber(
                        robotId, sessionId, 5L))
                .thenReturn(Optional.of(stored));

        RobotTelemetryService.IngestionResult result =
                telemetryService.ingestTelemetry(robotId, request);

        assertTrue(result.duplicate());
        assertFalse(result.latestStateUpdated());

        verify(telemetryRepository, never()).save(any(RobotTelemetry.class));
    }

    @Test
    void sameSequenceWithDifferentPayloadReturnsConflict() {
        Instant timestamp = Instant.now().minusSeconds(5);
        TelemetryRequest request = request(5L, 80, 10.0, 20.0, timestamp);

        RobotTelemetry stored = telemetry(
                robot, sessionId, 5L, 70, 10.0, 20.0, timestamp);

        when(telemetryRepository
                .findByRobot_IdAndSessionIdAndSequenceNumber(
                        robotId, sessionId, 5L))
                .thenReturn(Optional.of(stored));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> telemetryService.ingestTelemetry(robotId, request));

        assertEquals(409, exception.getStatusCode().value());
        verify(telemetryRepository, never()).save(any(RobotTelemetry.class));
    }

    @Test
    void olderSequenceIsStoredButDoesNotChangeLatestRobotState() {
        Instant timestamp = Instant.now().minusSeconds(5);
        TelemetryRequest request = request(9L, 40, 99.0, 88.0, timestamp);

        when(telemetryRepository
                .findByRobot_IdAndSessionIdAndSequenceNumber(
                        robotId, sessionId, 9L))
                .thenReturn(Optional.empty());

        RobotTelemetry latest = telemetry(
                robot, sessionId, 10L, 80, 10.0, 20.0,
                Instant.now().minusSeconds(10));

        when(telemetryRepository
                .findTopByRobot_IdAndSessionIdOrderBySequenceNumberDesc(
                        robotId, sessionId))
                .thenReturn(Optional.of(latest));

        when(telemetryRepository.save(any(RobotTelemetry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RobotTelemetryService.IngestionResult result =
                telemetryService.ingestTelemetry(robotId, request);

        assertFalse(result.duplicate());
        assertFalse(result.latestStateUpdated());
        assertEquals(80, robot.getBatteryPercentage());
        assertEquals(10.0, robot.getLocationX());
        assertEquals(20.0, robot.getLocationY());

        verify(telemetryRepository).save(any(RobotTelemetry.class));
    }

    private TelemetryRequest request(
            long sequence,
            int battery,
            double x,
            double y,
            Instant timestamp) {

        return new TelemetryRequest(
                sessionId,
                sequence,
                battery,
                new TelemetryRequest.Location(x, y),
                0.5,
                TelemetryRequest.HealthStatus.HEALTHY,
                timestamp);
    }

    private RobotTelemetry telemetry(
            Robot robot,
            UUID sessionId,
            long sequence,
            int battery,
            double x,
            double y,
            Instant timestamp) {

        RobotTelemetry telemetry = new RobotTelemetry();
        telemetry.setRobot(robot);
        telemetry.setSessionId(sessionId);
        telemetry.setSequenceNumber(sequence);
        telemetry.setBatteryPercentage(battery);
        telemetry.setLocationX(x);
        telemetry.setLocationY(y);
        telemetry.setSpeed(0.5);
        telemetry.setHealthStatus("HEALTHY");
        telemetry.setTimestamp(timestamp.truncatedTo(ChronoUnit.MICROS));        telemetry.setReceivedAt(timestamp);
        return telemetry;
    }
}