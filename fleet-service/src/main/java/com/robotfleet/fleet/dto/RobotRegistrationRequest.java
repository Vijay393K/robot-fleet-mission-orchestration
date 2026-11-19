
package com.robotfleet.fleet.dto;

import com.robotfleet.fleet.enums.RobotType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RobotRegistrationRequest {

    @NotBlank(message = "Robot name is required")
    @Size(max = 100, message = "Robot name cannot exceed 100 characters")
    private String name;

    @NotNull(message = "Robot type is required")
    private RobotType type;

    @Min(value = 0, message = "Battery cannot be below 0")
    @Max(value = 100, message = "Battery cannot exceed 100")
    private int batteryPercentage;

    private double locationX;

    private double locationY;

    public RobotRegistrationRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RobotType getType() {
        return type;
    }

    public void setType(RobotType type) {
        this.type = type;
    }

    public int getBatteryPercentage() {
        return batteryPercentage;
    }

    public void setBatteryPercentage(int batteryPercentage) {
        this.batteryPercentage = batteryPercentage;
    }

    public double getLocationX() {
        return locationX;
    }

    public void setLocationX(double locationX) {
        this.locationX = locationX;
    }

    public double getLocationY() {
        return locationY;
    }

    public void setLocationY(double locationY) {
        this.locationY = locationY;
    }
}
