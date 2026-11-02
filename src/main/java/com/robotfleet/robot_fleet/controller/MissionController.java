
package com.robotfleet.robot_fleet.controller;

import jakarta.validation.Valid;
import java.util.List;

import com.robotfleet.robot_fleet.MissionEvent;
import com.robotfleet.robot_fleet.dto.MissionEventResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.dto.MissionRequest;
import com.robotfleet.robot_fleet.dto.MissionResponse;
import com.robotfleet.robot_fleet.service.MissionService;
import com.robotfleet.robot_fleet.dto.MissionCompleteRequest;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.robot_fleet.MissionPriority;
import com.robotfleet.robot_fleet.MissionStatus;
import com.robotfleet.robot_fleet.MissionType;

import com.robotfleet.robot_fleet.dto.MissionStatusUpdateRequest;

import com.robotfleet.robot_fleet.dto.MissionCancelRequest;
import com.robotfleet.robot_fleet.dto.MissionAssignmentRequest;

import com.robotfleet.robot_fleet.dto.MissionStartRequest;
@RestController
@RequestMapping("/api/v1/missions")
public class MissionController {

    private final MissionService missionService;

    public MissionController(MissionService missionService) {
        this.missionService = missionService;
    }

    @PostMapping
    public ResponseEntity<MissionResponse> createMission(
            @Valid @RequestBody MissionRequest request) {

        Mission mission = missionService.createMission(request);

        MissionResponse response = new MissionResponse(mission);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/{missionId}")
    public ResponseEntity<MissionResponse> getMission(
            @PathVariable UUID missionId) {

        Mission mission = missionService.getMission(missionId);

        return ResponseEntity.ok(new MissionResponse(mission));
    }
    
    
    @GetMapping
    public ResponseEntity<Page<MissionResponse>> getAllMissions(

            @RequestParam(required = false) MissionStatus status,
            @RequestParam(required = false) MissionPriority priority,
            @RequestParam(required = false) MissionType missionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be >= 0 and size must be between 1 and 100"
            );
        }

        Page<Mission> missions = missionService.getFilteredMissions(
                status, priority, missionType, page, size
        );

        Page<MissionResponse> responses = missions.map(MissionResponse::new);

        return ResponseEntity.ok(responses);
    }
    @GetMapping("/{missionId}/events")
    public ResponseEntity<List<MissionEventResponse>> getMissionEvents(
            @PathVariable UUID missionId) {

        List<MissionEvent> events =
                missionService.getMissionEvents(missionId);

        List<MissionEventResponse> responses = events.stream()
                .map(MissionEventResponse::new)
                .toList();

        return ResponseEntity.ok(responses);
    }
    @PutMapping("/{missionId}/status")
    public MissionResponse changeMissionStatus(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionStatusUpdateRequest request) {

        Mission mission = missionService.changeMissionStatus(
                missionId,
                request.getStatus(),
                request.getActor(),
                request.getReason()
        );

        return new MissionResponse(mission);
    }
    
    @PutMapping("/{missionId}/cancel")
    public MissionResponse cancelMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionCancelRequest request) {

        Mission mission = missionService.changeMissionStatus(
                missionId,
                MissionStatus.CANCELLED,
                request.getActor(),
                request.getReason()
        );

        return new MissionResponse(mission);
    }
    
    @PutMapping("/{missionId}/assign")
    public MissionResponse assignMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionAssignmentRequest request) {

        Mission mission = missionService.assignMission(
                missionId,
                request.getRobotId(),
                request.getActor(),
                request.getReason()
        );

        return new MissionResponse(mission);
    }
    @PutMapping("/{missionId}/start")
    public MissionResponse startMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionStartRequest request) {

        Mission mission = missionService.startMission(
                missionId,
                request.getRobotId(),
                request.getActor(),
                request.getReason()
        );

        return new MissionResponse(mission);
    }
    
    @PutMapping("/{missionId}/complete")
    public MissionResponse completeMission(
            @PathVariable UUID missionId,
            @Valid @RequestBody MissionCompleteRequest request) {

        Mission mission = missionService.completeMission(
                missionId,
                request.getRobotId(),
                request.getActor(),
                request.getReason()
        );

        return new MissionResponse(mission);
    }
    @GetMapping("/robots/{robotId}/missions")
    public List<MissionResponse> getMissionsByRobot(
            @PathVariable UUID robotId) {

        return missionService.getMissionsByRobot(robotId)
                .stream()
                .map(MissionResponse::new)
                .toList();
    }
}
