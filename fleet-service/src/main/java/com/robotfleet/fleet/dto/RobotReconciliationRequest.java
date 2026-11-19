
package com.robotfleet.fleet.dto;

public record RobotReconciliationRequest(
        boolean physicallySafe,
        String operator
) {
}
