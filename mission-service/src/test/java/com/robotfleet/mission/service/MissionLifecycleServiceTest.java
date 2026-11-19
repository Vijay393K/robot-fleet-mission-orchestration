package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.dto.MissionCancelRequest;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionEventRepository;
import com.robotfleet.mission.repository.MissionRepository;

@ExtendWith(MockitoExtension.class)
class MissionLifecycleServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionEventRepository missionEventRepository;

    @Mock
    private MissionAssignmentOperationRepository operationRepository;

    @InjectMocks
    private MissionLifecycleService lifecycleService;

    @Test
    void assignedMissionCannotBeCancelled() {
        UUID missionId = UUID.randomUUID();

        Mission assignedMission = new Mission();
        assignedMission.setStatus(MissionStatus.ASSIGNED);
        assignedMission.setAssignedRobotId(UUID.randomUUID());

        when(missionRepository.findByIdForUpdate(missionId))
                .thenReturn(Optional.of(assignedMission));

        MissionCancelRequest request = new MissionCancelRequest(
                "operator",
                "Cancellation test");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> lifecycleService.cancelPendingMission(
                        missionId,
                        request,
                        "test-correlation-id"));

        assertEquals(409, exception.getStatusCode().value());

        verify(missionRepository, never()).saveAndFlush(assignedMission);
        verify(missionEventRepository, never())
                .save(org.mockito.ArgumentMatchers.any(MissionEvent.class));
    }
}