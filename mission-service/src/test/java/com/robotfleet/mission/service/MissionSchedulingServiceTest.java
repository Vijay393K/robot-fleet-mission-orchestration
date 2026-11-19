
package com.robotfleet.mission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse.RobotSummary;
import com.robotfleet.mission.dto.MissionAssignmentRequest;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.RobotType;
import com.robotfleet.mission.repository.MissionRepository;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.robotfleet.mission.exception.FleetServiceException;
import com.robotfleet.mission.exception.FleetServiceException.FailureType;
@ExtendWith(MockitoExtension.class)
class MissionSchedulingServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private FleetServiceClient fleetServiceClient;

    @Mock
    private MissionAssignmentIntentService assignmentIntentService;

    private MissionSchedulingService schedulingService;

    @BeforeEach
    void setUp() {
        schedulingService = new MissionSchedulingService(
                missionRepository,
                fleetServiceClient,
                assignmentIntentService,
                false,
                1,
                100
        );
    }

    @Test
    void shouldSelectNearestEligibleRobot() {

        Mission mission = createMission();

        RobotSummary distantRobot = createRobot(
                95, 50, 50, Instant.now()
        );

        RobotSummary nearestRobot = createRobot(
                95, 11, 11, Instant.now()
        );

        stubMissions(mission);
        stubRobots(List.of(distantRobot, nearestRobot));

        when(fleetServiceClient.getRobotAvailability(
                nearestRobot.id()
        )).thenReturn(available(nearestRobot));

        UUID operationId = UUID.randomUUID();

        when(assignmentIntentService.createAssignmentIntent(
                eq(mission.getId()),
                any(MissionAssignmentRequest.class),
                anyString()
        )).thenReturn(operationId);

        var result = schedulingService.executeScheduling();

        assertEquals(1, result.missionsExamined());
        assertEquals(1, result.intentsCreated());
        assertEquals(
                nearestRobot.id(),
                result.assignments().getFirst().robotId()
        );
        assertEquals(
                operationId,
                result.assignments().getFirst().operationId()
        );

        ArgumentCaptor<MissionAssignmentRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        MissionAssignmentRequest.class
                );

        verify(assignmentIntentService).createAssignmentIntent(
                eq(mission.getId()),
                requestCaptor.capture(),
                anyString()
        );

        assertEquals(
                nearestRobot.id(),
                requestCaptor.getValue().robotId()
        );
        assertEquals(
                "SCHEDULER",
                requestCaptor.getValue().actor()
        );

        verify(fleetServiceClient, never())
                .getRobotAvailability(distantRobot.id());
    }
    	
    
    @Test
    void shouldNotCreateAssignmentWhenFleetServiceIsUnavailable() {
        Mission mission = createMission();
        stubMissions(mission);

        when(fleetServiceClient.getIdleRobots(
                RobotType.CARRIER,
                25,
                0,
                100
        )).thenThrow(new FleetServiceException(
                "GET_IDLE_ROBOTS",
                FailureType.CONNECTION_FAILURE,
                null,
                false,
                "Unable to communicate with Fleet Service",
                null
        ));

        assertThrows(
                FleetServiceException.class,
                () -> schedulingService.executeScheduling()
        );

        verifyNoInteractions(assignmentIntentService);
    }
    
    
    
    @Test
    void shouldRejectLowBatteryAndStaleHeartbeat() {

        Mission mission = createMission();

        RobotSummary lowBatteryRobot = createRobot(
                20, 10, 10, Instant.now()
        );

        RobotSummary staleRobot = createRobot(
                90, 11, 11,
                Instant.now().minusSeconds(120)
        );

        stubMissions(mission);
        stubRobots(List.of(lowBatteryRobot, staleRobot));

        var result = schedulingService.executeScheduling();

        assertEquals(1, result.missionsExamined());
        assertEquals(0, result.intentsCreated());

        verifyNoInteractions(assignmentIntentService);

        verify(fleetServiceClient, never())
                .getRobotAvailability(any(UUID.class));
    }

    @Test
    void shouldSelectAlternativeWhenNearestIsUnavailable() {

        Mission mission = createMission();

        RobotSummary nearestRobot = createRobot(
                95, 10, 10, Instant.now()
        );

        RobotSummary alternativeRobot = createRobot(
                90, 15, 15, Instant.now()
        );

        stubMissions(mission);
        stubRobots(List.of(
                nearestRobot,
                alternativeRobot
        ));

        when(fleetServiceClient.getRobotAvailability(
                nearestRobot.id()
        )).thenReturn(new FleetRobotAvailabilityResponse(
                nearestRobot.id(),
                RobotType.CARRIER,
                false,
                95,
                new FleetRobotAvailabilityResponse.Location(10, 10),
                List.of("Robot unavailable")
        ));

        when(fleetServiceClient.getRobotAvailability(
                alternativeRobot.id()
        )).thenReturn(available(alternativeRobot));

        when(assignmentIntentService.createAssignmentIntent(
                eq(mission.getId()),
                any(MissionAssignmentRequest.class),
                anyString()
        )).thenReturn(UUID.randomUUID());

        var result = schedulingService.executeScheduling();

        assertEquals(1, result.intentsCreated());

        assertEquals(
                alternativeRobot.id(),
                result.assignments().getFirst().robotId()
        );

        verify(fleetServiceClient)
                .getRobotAvailability(nearestRobot.id());

        verify(fleetServiceClient)
                .getRobotAvailability(alternativeRobot.id());
    }

    private Mission createMission() {

        Mission mission = new Mission();

        ReflectionTestUtils.setField(
                mission,
                "id",
                UUID.randomUUID()
        );

        ReflectionTestUtils.setField(
                mission,
                "createdAt",
                Instant.parse("2026-11-17T09:00:00Z")
        );

        mission.setPriority(MissionPriority.HIGH);
        mission.setRequiredRobotType(RobotType.CARRIER);
        mission.setPickupX(10);
        mission.setPickupY(10);

        return mission;
    }

    private RobotSummary createRobot(
            int battery,
            double x,
            double y,
            Instant heartbeat) {

        return new RobotSummary(
                UUID.randomUUID(),
                "Scheduling-Test-Robot",
                RobotType.CARRIER,
                "IDLE",
                battery,
                x,
                y,
                heartbeat,
                null,
                false
        );
    }

    private FleetRobotAvailabilityResponse available(
            RobotSummary robot) {

        return new FleetRobotAvailabilityResponse(
                robot.id(),
                robot.type(),
                true,
                robot.batteryPercentage(),
                new FleetRobotAvailabilityResponse.Location(
                        robot.locationX(),
                        robot.locationY()
                ),
                List.of()
        );
    }

    private void stubMissions(Mission mission) {

        when(missionRepository.findSchedulableAfter(
                anyInt(),
                any(Instant.class),
                any(UUID.class),
                anyInt()
        )).thenReturn(
                List.of(mission),
                List.of()
        );
    }

    private void stubRobots(List<RobotSummary> robots) {

        when(fleetServiceClient.getIdleRobots(
                RobotType.CARRIER,
                25,
                0,
                100
        )).thenReturn(new FleetRobotPageResponse(
                robots,
                0,
                100,
                robots.size(),
                1
        ));
    }
}
