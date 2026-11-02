
package com.robotfleet.robot_fleet.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.robotfleet.robot_fleet.MissionPriority;
import com.robotfleet.robot_fleet.MissionType;
import com.robotfleet.robot_fleet.RobotType;

public class MissionRequest {

    @NotNull(message = "Mission type is required")
    private MissionType missionType;

    @NotNull(message = "Priority is required")
    private MissionPriority priority;

    @NotNull(message = "Required robot type is required")
    private RobotType requiredRobotType;

    @NotNull(message = "Pickup location is required")
    @Valid
    private Location pickupLocation;

    @NotNull(message = "Drop location is required")
    @Valid
    private Location dropLocation;

    public static class Location {

        @NotNull(message = "X coordinate is required")
        private Double x;

        @NotNull(message = "Y coordinate is required")
        private Double y;

        public Double getX() {
            return x;
        }

        public void setX(Double x) {
            this.x = x;
        }

        public Double getY() {
            return y;
        }

        public void setY(Double y) {
            this.y = y;
        }
    }

    public MissionType getMissionType() {
        return missionType;
    }

    public void setMissionType(MissionType missionType) {
        this.missionType = missionType;
    }

    public MissionPriority getPriority() {
        return priority;
    }

    public void setPriority(MissionPriority priority) {
        this.priority = priority;
    }

    public RobotType getRequiredRobotType() {
        return requiredRobotType;
    }

    public void setRequiredRobotType(RobotType requiredRobotType) {
        this.requiredRobotType = requiredRobotType;
    }

    public Location getPickupLocation() {
        return pickupLocation;
    }

    public void setPickupLocation(Location pickupLocation) {
        this.pickupLocation = pickupLocation;
    }

    public Location getDropLocation() {
        return dropLocation;
    }

    public void setDropLocation(Location dropLocation) {
        this.dropLocation = dropLocation;
    }
}
