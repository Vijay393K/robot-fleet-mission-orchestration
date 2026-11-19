package com.robotfleet.fleet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;
import com.robotfleet.fleet.repository.RobotRepository;

@ExtendWith(MockitoExtension.class)
class RobotFailureRecoveryServiceTest {

    @Mock
    private RobotRepository robotRepository;

    @InjectMocks
    private RobotFailureRecoveryService recoveryService;

    @Test
    void staleRobotIsMarkedOfflineAndPlacedOnSafetyHold() {
        Robot staleRobot = new Robot(
                UUID.randomUUID(),
                "Heartbeat-Test-Robot",
                RobotType.PICKER,
                80,
                10.0,
                20.0,
                RobotStatus.BUSY,
                Instant.now().minusSeconds(60),
                UUID.randomUUID(),
                UUID.randomUUID());

        when(robotRepository.findStaleRobotsForRecovery(any(Instant.class)))
                .thenReturn(List.of(staleRobot));

        int recoveredCount = recoveryService.detectAndRecoverOfflineRobots();

        assertEquals(1, recoveredCount);
        assertEquals(RobotStatus.OFFLINE, staleRobot.getStatus());
        assertTrue(staleRobot.isSafetyHold());
    }

    @Test
    void returnsZeroWhenThereAreNoStaleRobots() {
        when(robotRepository.findStaleRobotsForRecovery(any(Instant.class)))
                .thenReturn(List.of());

        int recoveredCount = recoveryService.detectAndRecoverOfflineRobots();

        assertEquals(0, recoveredCount);
    }
}