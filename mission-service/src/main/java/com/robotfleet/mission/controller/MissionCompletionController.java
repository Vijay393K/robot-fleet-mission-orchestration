
package com.robotfleet.mission.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.mission.dto.MissionCompleteRequest;
import com.robotfleet.mission.dto.MissionResponse;
import com.robotfleet.mission.service.MissionCompletionCoordinator;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/missions")
public class MissionCompletionController {

    private final MissionCompletionCoordinator
            completionCoordinator;

    public MissionCompletionController(
            MissionCompletionCoordinator completionCoordinator) {

        this.completionCoordinator = completionCoordinator;
    }

    @PostMapping("/{missionId}/complete")
    public ResponseEntity<MissionResponse> completeMission(
            @PathVariable UUID missionId,

            @Valid @RequestBody
            MissionCompleteRequest request,

            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false
            )
            String correlationId) {

        MissionResponse response =
                completionCoordinator.completeMission(
                        missionId,
                        request,
                        correlationId
                );

        return ResponseEntity.ok(response);
    }
}
