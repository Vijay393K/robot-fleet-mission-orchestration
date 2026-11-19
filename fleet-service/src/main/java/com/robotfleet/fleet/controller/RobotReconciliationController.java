
package com.robotfleet.fleet.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.fleet.dto.RobotReconciliationRequest;
import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.service.RobotReconciliationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/robots")
public class RobotReconciliationController {

    private final RobotReconciliationService reconciliationService;

    public RobotReconciliationController(
            RobotReconciliationService reconciliationService) {

        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/{robotId}/reconcile")
    public ResponseEntity<Robot> reconcileRobot(
            @PathVariable UUID robotId,
            @Valid @RequestBody RobotReconciliationRequest request) {

        Robot robot = reconciliationService.reconcileRobot(
                robotId,
                request.physicallySafe(),
                request.operator());

        return ResponseEntity.ok(robot);
    }
}
