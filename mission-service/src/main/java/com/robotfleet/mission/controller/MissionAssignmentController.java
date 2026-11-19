
package com.robotfleet.mission.controller;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.dto.MissionAssignmentRequest;
import com.robotfleet.mission.entity.MissionAssignmentOperation;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.service.IdempotencyService;
import com.robotfleet.mission.service.MissionAssignmentIntentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/missions/{missionId}")
public class MissionAssignmentController {

    private static final String ASSIGN_SCOPE_PREFIX =
            "MISSION_ASSIGN:";

    private final MissionAssignmentIntentService intentService;
    private final MissionAssignmentOperationRepository operationRepository;
    private final IdempotencyService idempotencyService;

    public MissionAssignmentController(
            MissionAssignmentIntentService intentService,
            MissionAssignmentOperationRepository operationRepository,
            IdempotencyService idempotencyService) {

        this.intentService = intentService;
        this.operationRepository = operationRepository;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/assign")
    public ResponseEntity<AssignmentIntentResponse> assignMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionAssignmentRequest request,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey,
            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false
            ) String correlationId) {

        String scope = ASSIGN_SCOPE_PREFIX + missionId;

        ResponseEntity<AssignmentIntentResponse> result =
                idempotencyService.execute(
                        scope,
                        idempotencyKey,
                        request,
                        AssignmentIntentResponse.class,
                        HttpStatus.ACCEPTED,
                        () -> {
                            UUID operationId =
                                    intentService.createAssignmentIntent(
                                            missionId,
                                            request,
                                            correlationId
                                    );

                            return new AssignmentIntentResponse(
                                    operationId,
                                    missionId,
                                    request.robotId(),
                                    AssignmentOperationStatus.RESERVE_PENDING
                            );
                        },
                        AssignmentIntentResponse::operationId
                );

        AssignmentIntentResponse response = result.getBody();

        if (response == null) {
            throw new IllegalStateException(
                    "Assignment request returned an empty response"
            );
        }

        URI location = URI.create(
                "/api/v1/missions/"
                        + missionId
                        + "/assignment-operations/"
                        + response.operationId()
        );

        return ResponseEntity
                .status(result.getStatusCode())
                .location(location)
                .body(response);
    }

    @GetMapping("/assignment-operations/{operationId}")
    public ResponseEntity<AssignmentOperationResponse>
            getAssignmentOperation(
                    @PathVariable UUID missionId,
                    @PathVariable UUID operationId) {

        MissionAssignmentOperation operation =
                operationRepository.findById(operationId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Assignment operation not found"
                                )
                        );

        if (!operation.getMissionId().equals(missionId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Assignment operation not found for this mission"
            );
        }

        return ResponseEntity.ok(
                new AssignmentOperationResponse(
                        operation.getId(),
                        operation.getMissionId(),
                        operation.getRobotId(),
                        operation.getAssignmentGeneration(),
                        operation.getStatus(),
                        operation.getReservationId(),
                        operation.getAttemptCount(),
                        operation.getNextAttemptAt(),
                        operation.getLastErrorCode(),
                        operation.getCreatedAt(),
                        operation.getUpdatedAt()
                )
        );
    }

    public record AssignmentIntentResponse(
            UUID operationId,
            UUID missionId,
            UUID robotId,
            AssignmentOperationStatus initialStatus
    ) {
    }

    public record AssignmentOperationResponse(
            UUID operationId,
            UUID missionId,
            UUID robotId,
            long assignmentGeneration,
            AssignmentOperationStatus status,
            UUID reservationId,
            long attemptCount,
            Instant nextAttemptAt,
            String lastErrorCode,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
