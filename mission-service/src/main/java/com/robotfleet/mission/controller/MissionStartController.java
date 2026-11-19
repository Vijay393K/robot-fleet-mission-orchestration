
package com.robotfleet.mission.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.mission.dto.MissionResponse;
import com.robotfleet.mission.dto.MissionStartRequest;
import com.robotfleet.mission.service.MissionStartCoordinator;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/missions")
public class MissionStartController {

    private final MissionStartCoordinator
            missionStartCoordinator;

    public MissionStartController(
            MissionStartCoordinator missionStartCoordinator) {

        this.missionStartCoordinator =
                missionStartCoordinator;
    }

    @PostMapping("/{missionId}/start")
    public ResponseEntity<MissionResponse> startMission(
            @PathVariable UUID missionId,

            @Valid @RequestBody
            MissionStartRequest request,

            @RequestHeader(
                    value = "X-Correlation-ID",
                    required = false
            )
            String correlationId) {

        MissionResponse response =
                missionStartCoordinator.startMission(
                        missionId,
                        request,
                        correlationId
                );

        return ResponseEntity.ok(response);
    }
}
