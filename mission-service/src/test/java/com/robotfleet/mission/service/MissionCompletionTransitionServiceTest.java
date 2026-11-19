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

import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.entity.MissionAssignment;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.entity.MissionEvent;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.repository.MissionAssignmentRepository;
import com.robotfleet.mission.repository.MissionEventRepository;
import com.robotfleet.mission.repository.MissionRepository;

@ExtendWith(MockitoExtension.class)
class MissionCompletionTransitionServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private MissionAssignmentRepository assignmentRepository;

    @Mock
    private MissionAssignmentOperationRepository operationRepository;

    @Mock
    private MissionEventRepository eventRepository;

    @InjectMocks
    private MissionCompletionTransitionService transitionService;

    @Test
    void rejectsCompletionFromOldRobotAndAssignmentGeneration() {
        UUID missionId = UUID.randomUUID();
        UUID oldRobotId = UUID.randomUUID();
        UUID currentlyAssignedRobotId = UUID.randomUUID();

        Mission mission = new Mission();
        mission.setStatus(MissionStatus.IN_PROGRESS);
        mission.setAssignedRobotId(currentlyAssignedRobotId);
        mission.setAssignmentGeneration(2);

        when(missionRepository.findByIdForUpdate(missionId))
                .thenReturn(Optional.of(mission));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transitionService.completeMission(
                        missionId,
                        oldRobotId,
                        1,
                        "ROBOT",
                        "Delayed completion",
                        "stale-completion-test",
                        null));

        assertEquals(409, exception.getStatusCode().value());
        assertEquals(MissionStatus.IN_PROGRESS, mission.getStatus());
        assertEquals(currentlyAssignedRobotId, mission.getAssignedRobotId());
        assertEquals(2, mission.getAssignmentGeneration());

        verify(missionRepository, never())
                .saveAndFlush(org.mockito.ArgumentMatchers.any(Mission.class));
        verify(assignmentRepository, never())
                .findActiveByMissionIdForUpdate(missionId);
        verify(operationRepository, never())
                .findByMissionIdAndAssignmentGeneration(missionId, 1);
        verify(eventRepository, never())
                .save(org.mockito.ArgumentMatchers.any(MissionEvent.class));
    }
}