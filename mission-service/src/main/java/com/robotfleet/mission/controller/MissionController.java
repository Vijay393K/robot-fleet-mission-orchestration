
package com.robotfleet.mission.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.mission.dto.MissionCancelRequest;
import com.robotfleet.mission.dto.MissionEventResponse;
import com.robotfleet.mission.dto.MissionRequest;
import com.robotfleet.mission.dto.MissionResponse;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.MissionStatus;
import com.robotfleet.mission.enums.MissionType;
import com.robotfleet.mission.service.IdempotencyService;
import com.robotfleet.mission.service.MissionLifecycleService;
import com.robotfleet.mission.service.MissionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/missions")
public class MissionController {

    private static final String MISSION_CREATE_SCOPE =
            "MISSION_CREATE";

    private final MissionService missionService;
    private final IdempotencyService idempotencyService;
    private final MissionLifecycleService lifecycleService;

    public MissionController(
            MissionService missionService,
            IdempotencyService idempotencyService,
            MissionLifecycleService lifecycleService) {

        this.missionService = missionService;
        this.idempotencyService = idempotencyService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    public ResponseEntity<MissionResponse> createMission(
            @Valid @RequestBody MissionRequest request,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            ) String idempotencyKey) {

        ResponseEntity<MissionResponse> result =
                idempotencyService.execute(
                        MISSION_CREATE_SCOPE,
                        idempotencyKey,
                        request,
                        MissionResponse.class,
                        HttpStatus.CREATED,
                        () -> missionService.createMission(request),
                        MissionResponse::id
                );

        MissionResponse response = result.getBody();

        if (response == null) {
            throw new IllegalStateException(
                    "Mission creation returned an empty response"
            );
        }

        URI location = URI.create(
                "/api/v1/missions/" + response.id()
        );

        return ResponseEntity
                .status(result.getStatusCode())
                .location(location)
                .body(response);
    }

    @GetMapping("/{missionId}")
    public ResponseEntity<MissionResponse> getMission(
            @PathVariable UUID missionId) {

        return ResponseEntity.ok(
                missionService.getMission(missionId)
        );
    }

    @GetMapping
    public ResponseEntity<MissionPageResponse> getMissions(
            @RequestParam(required = false)
            MissionStatus status,

            @RequestParam(required = false)
            MissionPriority priority,

            @RequestParam(required = false)
            MissionType missionType,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size) {

        Page<MissionResponse> result =
                missionService.getFilteredMissions(
                        status,
                        priority,
                        missionType,
                        page,
                        size
                );

        return ResponseEntity.ok(
                new MissionPageResponse(
                        result.getContent(),
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages()
                )
        );
    }

    @GetMapping("/{missionId}/events")
    public ResponseEntity<MissionEventPageResponse> getMissionEvents(
            @PathVariable UUID missionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<MissionEventResponse> result =
                missionService.getMissionEvents(
                        missionId,
                        page,
                        size
                );

        return ResponseEntity.ok(
                new MissionEventPageResponse(
                        result.getContent(),
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages()
                )
        );
    }

    @PostMapping("/{missionId}/cancel")
    public ResponseEntity<MissionResponse> cancelMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionCancelRequest request,
            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false
            ) String correlationId) {

        MissionResponse response =
                lifecycleService.cancelPendingMission(
                        missionId,
                        request,
                        correlationId
                );

        return ResponseEntity.ok(response);
    }

    public record MissionPageResponse(
            List<MissionResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record MissionEventPageResponse(
            List<MissionEventResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
