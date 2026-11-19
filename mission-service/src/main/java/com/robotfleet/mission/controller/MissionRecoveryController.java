
package com.robotfleet.mission.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.mission.service.MissionRecoveryReleaseIntentService;
import com.robotfleet.mission.service.MissionRecoveryReleaseIntentService.RecoveryReleaseIntent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/missions")
public class MissionRecoveryController {

    private final MissionRecoveryReleaseIntentService
            recoveryReleaseIntentService;

    public MissionRecoveryController(
            MissionRecoveryReleaseIntentService
                    recoveryReleaseIntentService) {

        this.recoveryReleaseIntentService =
                recoveryReleaseIntentService;
    }

    @PostMapping("/{missionId}/recovery/release")
    public ResponseEntity<RecoveryReleaseIntent>
            initiateRecoveryRelease(
                    @PathVariable UUID missionId,
                    @Valid @RequestBody
                    RecoveryReleaseRequest request) {

        RecoveryReleaseIntent response =
                recoveryReleaseIntentService.prepareRelease(
                        missionId,
                        request.operator(),
                        request.physicallySafe(),
                        request.executionStopped()
                );

        return ResponseEntity
                .accepted()
                .body(response);
    }

    public record RecoveryReleaseRequest(

            @NotBlank(message = "Operator is required")
            @Size(
                    max = 100,
                    message = "Operator must not exceed 100 characters"
            )
            String operator,

            boolean physicallySafe,

            boolean executionStopped
    ) {
    }
}
