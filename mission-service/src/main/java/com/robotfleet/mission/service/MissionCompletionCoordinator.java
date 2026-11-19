
package com.robotfleet.mission.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotStateResponse;
import com.robotfleet.mission.dto.MissionCompleteRequest;
import com.robotfleet.mission.dto.MissionResponse;

@Service
public class MissionCompletionCoordinator {

    private final FleetServiceClient fleetServiceClient;

    private final MissionCompletionTransitionService
            completionTransitionService;

    public MissionCompletionCoordinator(
            FleetServiceClient fleetServiceClient,
            MissionCompletionTransitionService
                    completionTransitionService) {

        this.fleetServiceClient = fleetServiceClient;
        this.completionTransitionService =
                completionTransitionService;
    }

    public MissionResponse completeMission(
            UUID missionId,
            MissionCompleteRequest request,
            String correlationId) {

        validateRequest(missionId, request);

        // Step 1:
        // Retrieve the latest robot state through Fleet REST.
        // No Mission Service database transaction is active.

        FleetRobotStateResponse fleetState =
                fleetServiceClient.getRobotState(
                        request.robotId()
                );

        // Step 2:
        // Execute the mission completion transaction.
        // This atomically updates the mission, records
        // the completion event and creates release intent.

        return completionTransitionService.completeMission(
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
            MissionCompleteRequest request) {

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
                    "Mission completion reason is required"
            );
        }
    }
}
