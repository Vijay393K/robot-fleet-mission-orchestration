
package com.robotfleet.mission.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.mission.dto.MissionAssignmentResponse;
import com.robotfleet.mission.service.MissionService;

@RestController
public class AssignmentHistoryController {

    private final MissionService missionService;

    public AssignmentHistoryController(
            MissionService missionService) {

        this.missionService = missionService;
    }

    @GetMapping("/api/v1/missions/{missionId}/assignments")
    public ResponseEntity<AssignmentPageResponse> getMissionAssignments(
            @PathVariable UUID missionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<MissionAssignmentResponse> result =
                missionService.getMissionAssignments(
                        missionId,
                        page,
                        size
                );

        return ResponseEntity.ok(
                AssignmentPageResponse.from(result)
        );
    }

    @GetMapping("/api/v1/robots/{robotId}/assignments")
    public ResponseEntity<AssignmentPageResponse> getRobotAssignments(
            @PathVariable UUID robotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<MissionAssignmentResponse> result =
                missionService.getRobotAssignments(
                        robotId,
                        page,
                        size
                );

        return ResponseEntity.ok(
                AssignmentPageResponse.from(result)
        );
    }

    public record AssignmentPageResponse(
            List<MissionAssignmentResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {

        public static AssignmentPageResponse from(
                Page<MissionAssignmentResponse> result) {

            return new AssignmentPageResponse(
                    result.getContent(),
                    result.getNumber(),
                    result.getSize(),
                    result.getTotalElements(),
                    result.getTotalPages()
            );
        }
    }
}
