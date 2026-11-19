
package com.robotfleet.fleet.controller;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.robotfleet.fleet.dto.TelemetryHistoryResponse;
import com.robotfleet.fleet.dto.TelemetryIngestionResponse;
import com.robotfleet.fleet.dto.TelemetryRequest;
import com.robotfleet.fleet.service.RobotTelemetryService;
import com.robotfleet.fleet.service.RobotTelemetryService.IngestionResult;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/robots/{robotId}/telemetry")
public class RobotTelemetryController {

    private final RobotTelemetryService telemetryService;

    public RobotTelemetryController(
            RobotTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<TelemetryIngestionResponse> ingestTelemetry(
            @PathVariable UUID robotId,
            @Valid @RequestBody TelemetryRequest request) {

        IngestionResult result =
                telemetryService.ingestTelemetry(robotId, request);

        TelemetryIngestionResponse response =
                new TelemetryIngestionResponse(
                        result.telemetryId(),
                        result.robotId(),
                        result.sessionId(),
                        result.sequenceNumber(),
                        result.duplicate(),
                        result.latestStateUpdated(),
                        result.message());

        HttpStatus status = result.duplicate()
                ? HttpStatus.OK
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<TelemetryHistoryResponse>> getTelemetryHistory(
            @PathVariable UUID robotId,
            @RequestParam(name = "from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,
            @RequestParam(name = "to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,
            @RequestParam(name = "page", defaultValue = "0")
            int page,
            @RequestParam(name = "size", defaultValue = "10")
            int size) {

        Page<TelemetryHistoryResponse> history =
                telemetryService.getTelemetryHistory(
                        robotId,
                        from,
                        to,
                        page,
                        size);

        return ResponseEntity.ok(history);
    }
}
