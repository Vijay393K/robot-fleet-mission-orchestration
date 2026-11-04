package com.robotfleet.robot_fleet.dto;

public record RobotReconciliationRequest(
        boolean physicallySafe,
        String operator
) {
}
