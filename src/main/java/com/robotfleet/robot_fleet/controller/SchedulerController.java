
package com.robotfleet.robot_fleet.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.service.MissionSchedulerService;

@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

    private final MissionSchedulerService schedulerService;

    public SchedulerController(
            MissionSchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    @PostMapping("/execute")
    public ResponseEntity<List<Mission>> executeScheduling() {

        List<Mission> assignedMissions =
                schedulerService.executeScheduling();

        return ResponseEntity.ok(assignedMissions);
    }
}
