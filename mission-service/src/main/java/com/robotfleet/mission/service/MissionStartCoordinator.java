
package com.robotfleet.mission.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotStateResponse;
import com.robotfleet.mission.dto.MissionResponse;
import com.robotfleet.mission.dto.MissionStartRequest;

@Service
public class MissionStartCoordinator {

    private final FleetServiceClient fleetServiceClient;

    private final MissionStartTransitionService
            missionStartTransitionService;

    public MissionStartCoordinator(
            FleetServiceClient fleetServiceClient,
            MissionStartTransitionService
                    missionStartTransitionService) {

        this.fleetServiceClient = fleetServiceClient;

        this.missionStartTransitionService =
                missionStartTransitionService;
    }

    public MissionResponse startMission(
            UUID missionId,
            MissionStartRequest request,
            String correlationId) {

        validateRequest(missionId, request);

        // Step 1:
        // Fetch the robot's current state from Fleet Service.
        // This REST call is outside a database transaction.

        FleetRobotStateResponse fleetState =
                fleetServiceClient.getRobotState(
                        request.robotId()
                );

        // Step 2:
        // Validate the mission and assignment under a
        // database lock, then persist the status transition
        // and mission event in mission_db.

        return missionStartTransitionService
                .startAssignedMission(
                        missionId,
                        request.robotId(),
                        request.assignmentGeneration(),
                        request.actor(),
                        request.reason(),
                        correlationId,
                        fleetState
                );
    }

    private void validateRequest(
            UUID missionId,
            MissionStartRequest request) {

        if (missionId == null || request == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission ID and request body are required"
            );
        }

        if (request.robotId() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Robot ID is required"
            );
        }

        if (request.assignmentGeneration() == null
                || request.assignmentGeneration() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A positive assignment generation is required"
            );
        }

        if (request.actor() == null
                || request.actor().isBlank()
                || request.actor().length() > 100) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid actor is required"
            );
        }

        if (request.reason() == null
                || request.reason().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Mission start reason is required"
            );
        }
    }
}
