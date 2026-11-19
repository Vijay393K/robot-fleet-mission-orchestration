package com.robotfleet.fleet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.entity.RobotHeartbeat;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;
import com.robotfleet.fleet.repository.RobotHeartbeatRepository;
import com.robotfleet.fleet.repository.RobotRepository;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private RobotRepository robotRepository;

    @Mock
    private RobotHeartbeatRepository heartbeatRepository;

    @InjectMocks
    private HeartbeatService heartbeatService;

    private UUID robotId;
    private Robot robot;

    @BeforeEach
    void setUp() {
        robotId = UUID.randomUUID();

        robot = new Robot(
                robotId,
                "Heartbeat-Test-Robot",
                RobotType.PICKER,
                80,
                10.0,
                20.0,
                RobotStatus.IDLE,
                Instant.now().minusSeconds(60),
                null,
                UUID.randomUUID());

        robot.setLastHeartbeatSequence(5);
    }

    @Test
    void newerHeartbeatUpdatesRobotAndStoresHeartbeat() {
        when(robotRepository.findByIdForUpdate(robotId))
                .thenReturn(Optional.of(robot));

        Robot result = heartbeatService.processHeartbeat(robotId, 6);

        assertEquals(6, result.getLastHeartbeatSequence());
        assertNotNull(result.getLastHeartbeat());
        verify(heartbeatRepository).save(any(RobotHeartbeat.class));
    }

    @Test
    void duplicateOrOlderHeartbeatReturnsConflict() {
        when(robotRepository.findByIdForUpdate(robotId))
                .thenReturn(Optional.of(robot));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> heartbeatService.processHeartbeat(robotId, 5));

        assertEquals(409, exception.getStatusCode().value());
        verify(heartbeatRepository, never()).save(any(RobotHeartbeat.class));
    }

    @Test
    void nonPositiveHeartbeatSequenceReturnsBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> heartbeatService.processHeartbeat(robotId, 0));

        assertEquals(400, exception.getStatusCode().value());
        verify(robotRepository, never()).findByIdForUpdate(robotId);
        verify(heartbeatRepository, never()).save(any(RobotHeartbeat.class));
    }
}