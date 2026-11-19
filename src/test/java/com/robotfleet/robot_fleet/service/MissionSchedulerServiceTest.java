
package com.robotfleet.robot_fleet.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.robotfleet.robot_fleet.MissionStatus;
import com.robotfleet.robot_fleet.repository.MissionAssignmentRepository;
import com.robotfleet.robot_fleet.repository.MissionEventRepository;
import com.robotfleet.robot_fleet.repository.MissionRepository;
import com.robotfleet.robot_fleet.repository.RobotRepository;

class MissionSchedulerServiceTest {

    private MissionRepository missionRepository;
    private RobotRepository robotRepository;
    private MissionEventRepository missionEventRepository;
    private MissionAssignmentRepository missionAssignmentRepository;

    private MissionSchedulerService schedulerService;

    @BeforeEach
    void setUp() {

        missionRepository = mock(MissionRepository.class);
        robotRepository = mock(RobotRepository.class);
        missionEventRepository = mock(MissionEventRepository.class);
        missionAssignmentRepository = mock(MissionAssignmentRepository.class);

        schedulerService = new MissionSchedulerService(
                missionRepository,
                robotRepository,
                missionEventRepository,
                missionAssignmentRepository
        );
    }

    @Test
    void shouldReturnEmptyListWhenNoPendingMissions() {

        when(missionRepository.findPendingMissionsForScheduling(
                MissionStatus.PENDING
        )).thenReturn(List.of());

        List<com.robotfleet.robot_fleet.Mission> result =
                schedulerService.executeScheduling();

        assertTrue(result.isEmpty());

        verify(missionRepository).findPendingMissionsForScheduling(
                MissionStatus.PENDING
        );

        verifyNoInteractions(robotRepository);
        verifyNoInteractions(missionEventRepository);
        verifyNoInteractions(missionAssignmentRepository);
    }
}
