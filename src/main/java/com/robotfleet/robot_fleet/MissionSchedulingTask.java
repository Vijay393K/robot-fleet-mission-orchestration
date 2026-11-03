package com.robotfleet.robot_fleet;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.robotfleet.robot_fleet.service.MissionSchedulerService;

@Component
public class MissionSchedulingTask {

    private final MissionSchedulerService missionSchedulerService;

    public MissionSchedulingTask(
            MissionSchedulerService missionSchedulerService) {
        this.missionSchedulerService = missionSchedulerService;
    }

    @Scheduled(fixedDelay = 5000)
    public void schedulePendingMissions() {

        missionSchedulerService.executeScheduling();
    }
}