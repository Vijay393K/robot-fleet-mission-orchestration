
package com.robotfleet.robot_fleet;

import java.time.Instant;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "robots")
public class Robot {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RobotType type;

    @Column(name = "battery_percentage", nullable = false)
    private int batteryPercentage;

    @Column(name = "location_x", nullable = false)
    private double locationX;

    @Column(name = "location_y", nullable = false)
    private double locationY;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RobotStatus status;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "current_mission_id")
    private UUID currentMissionId;
    
    @Column(name = "current_session_id")
    private UUID currentSessionId;
    
    @Column(name = "last_heartbeat_sequence", nullable = false)
    private long lastHeartbeatSequence = 0;
    public Robot() {
    }
    @Column(name = "safety_hold", nullable = false)
    private boolean safetyHold = false;

    public Robot(UUID id, String name, RobotType type,
            int batteryPercentage, double locationX,
            double locationY, RobotStatus status,
            Instant lastHeartbeat, UUID currentMissionId,
            UUID currentSessionId) {

   this.id = id;
   this.name = name;
   this.type = type;
   this.batteryPercentage = batteryPercentage;
   this.locationX = locationX;
   this.locationY = locationY;
   this.status = status;
   this.lastHeartbeat = lastHeartbeat;
   this.currentMissionId = currentMissionId;
   this.currentSessionId = currentSessionId;
}

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public RobotStatus getStatus() {
        return status;
    }

    public void setStatus(RobotStatus status) {
        this.status = status;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void setLastHeartbeat(Instant lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public UUID getCurrentMissionId() {
        return currentMissionId;
    }

    public void setCurrentMissionId(UUID currentMissionId) {
        this.currentMissionId = currentMissionId;
    }
    
    public UUID getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(UUID currentSessionId) {
        this.currentSessionId = currentSessionId;
    }
    
    public long getLastHeartbeatSequence() {
        return lastHeartbeatSequence;
    }

    public void setLastHeartbeatSequence(long lastHeartbeatSequence) {
        this.lastHeartbeatSequence = lastHeartbeatSequence;
    }
    public boolean isSafetyHold() {
        return safetyHold;
    }

    public void setSafetyHold(boolean safetyHold) {
        this.safetyHold = safetyHold;
    }
}
