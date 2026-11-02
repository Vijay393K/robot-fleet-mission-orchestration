
package com.robotfleet.robot_fleet.dto;

import java.time.Instant;
import java.util.UUID;

import com.robotfleet.robot_fleet.Mission;
import com.robotfleet.robot_fleet.MissionPriority;
import com.robotfleet.robot_fleet.MissionStatus;
import com.robotfleet.robot_fleet.MissionType;
import com.robotfleet.robot_fleet.RobotType;

public class MissionResponse {

    private UUID id;
    private MissionType missionType;
    private MissionPriority priority;
    private RobotType requiredRobotType;
    private MissionStatus status;
    private MissionRequest.Location pickupLocation;
    private MissionRequest.Location dropLocation;
    private Instant createdAt;
    private Instant updatedAt;

    public MissionResponse(Mission mission) {

        this.id = mission.getId();
        this.missionType = mission.getMissionType();
        this.priority = mission.getPriority();
        this.requiredRobotType = mission.getRequiredRobotType();
        this.status = mission.getStatus();

        MissionRequest.Location pickup = new MissionRequest.Location();
        pickup.setX(mission.getPickupX());
        pickup.setY(mission.getPickupY());
        this.pickupLocation = pickup;

        MissionRequest.Location drop = new MissionRequest.Location();
        drop.setX(mission.getDropX());
        drop.setY(mission.getDropY());
        this.dropLocation = drop;

        this.createdAt = mission.getCreatedAt();
        this.updatedAt = mission.getUpdatedAt();
    }

    public UUID getId() {
        return id;
    }

    public MissionType getMissionType() {
        return missionType;
    }

    public MissionPriority getPriority() {
        return priority;
    }

    public RobotType getRequiredRobotType() {
        return requiredRobotType;
    }

    public MissionStatus getStatus() {
        return status;
    }

    public MissionRequest.Location getPickupLocation() {
        return pickupLocation;
    }

    public MissionRequest.Location getDropLocation() {
        return dropLocation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
