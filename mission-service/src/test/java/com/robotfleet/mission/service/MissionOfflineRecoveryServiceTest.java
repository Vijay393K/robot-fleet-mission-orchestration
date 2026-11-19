package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.robotfleet.mission.dto.FleetRobotStateResponse;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionEventRepository;
import com.robotfleet.mission.repository.MissionRepository;
import com.robotfleet.mission.service.MissionOfflineRecoveryService.RecoveryResult;

@ExtendWith(MockitoExtension.class)
class MissionOfflineRecoveryServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionEventRepository eventRepository;

    @InjectMocks
    private MissionOfflineRecoveryService recoveryService;

    @Test
    void offlineAssignedRobotMovesMissionToRecoveryAndRecordsEvent() {
        UUID missionId = UUID.randomUUID();
        UUID robotId = UUID.randomUUID();

        Mission mission = new Mission();
        ReflectionTestUtils.setField(mission, "id", missionId);
        mission.setStatus(MissionStatus.IN_PROGRESS);
        mission.setAssignedRobotId(robotId);
        mission.setAssignmentGeneration(1);

        when(missionRepository.findByIdForUpdate(missionId))
                .thenReturn(Optional.of(mission));

        FleetRobotStateResponse robotState =
                new FleetRobotStateResponse(
                        robotId,
                        "OFFLINE",
                        missionId,
                        true,
                        Instant.now().minusSeconds(40));

        RecoveryResult result = recoveryService.markRecoveryPending(
                missionId,
                robotState,
                "offline-test");

        assertEquals(RecoveryResult.RECOVERY_STARTED, result);
        assertEquals(MissionStatus.RECOVERY_PENDING, mission.getStatus());
        verify(missionRepository).saveAndFlush(mission);
        verify(eventRepository).save(any(MissionEvent.class));
    }

    @Test
    void onlineRobotDoesNotTriggerMissionRecovery() {
        UUID missionId = UUID.randomUUID();
        UUID robotId = UUID.randomUUID();

        FleetRobotStateResponse robotState =
                new FleetRobotStateResponse(
                        robotId,
                        "IDLE",
                        null,
                        false,
                        Instant.now());

        RecoveryResult result = recoveryService.markRecoveryPending(
                missionId,
                robotState,
                "online-test");

        assertEquals(RecoveryResult.ROBOT_NOT_OFFLINE, result);
        verify(missionRepository, never())
                .findByIdForUpdate(missionId);
        verify(missionRepository, never()).saveAndFlush(any(Mission.class));
        verify(eventRepository, never()).save(any(MissionEvent.class));
    }
}