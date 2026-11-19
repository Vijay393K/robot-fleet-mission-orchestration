
package com.robotfleet.mission.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.mission.service.MissionSchedulingService;
import com.robotfleet.mission.service.MissionSchedulingService.SchedulingResult;

@RestController
@RequestMapping("/api/v1/scheduler")
public class MissionSchedulingController {

    private final MissionSchedulingService schedulingService;

    public MissionSchedulingController(
            MissionSchedulingService schedulingService) {
        this.schedulingService = schedulingService;
    }

    @PostMapping("/execute")
    public SchedulingResult execute() {
        return schedulingService.executeScheduling();
    }
}
