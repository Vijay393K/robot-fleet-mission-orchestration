
package com.robotfleet.fleet.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.robotfleet.fleet.dto.ReserveRobotRequest;
import com.robotfleet.fleet.dto.ReservationResponse;
import com.robotfleet.fleet.entity.RobotReservation;
import com.robotfleet.fleet.service.RobotReservationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reservations")
public class RobotReservationController {

    private static final String OPERATION_SCOPE = "RESERVE_ROBOT";

    private final RobotReservationService reservationService;

    public RobotReservationController(
            RobotReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> reserveRobot(
            @Valid @RequestBody ReserveRobotRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String requestHash = createRequestHash(request);

        RobotReservation reservation = reservationService.reserveRobot(
                request.getRobotId(),
                request.getMissionId(),
                OPERATION_SCOPE,
                idempotencyKey,
                requestHash);

        ReservationResponse response = toResponse(reservation);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{reservationId}/confirm")
    public ResponseEntity<ReservationResponse> confirmReservation(
            @PathVariable UUID reservationId) {

        RobotReservation reservation =
                reservationService.confirmReservation(reservationId);

        return ResponseEntity.ok(toResponse(reservation));
    }

    @PostMapping("/{reservationId}/release")
    public ResponseEntity<ReservationResponse> releaseReservation(
            @PathVariable UUID reservationId) {

        RobotReservation reservation =
                reservationService.releaseReservation(reservationId);

        return ResponseEntity.ok(toResponse(reservation));
    }

    private ReservationResponse toResponse(
            RobotReservation reservation) {

        ReservationResponse response = new ReservationResponse();

        response.setReservationId(reservation.getId());
        response.setRobotId(reservation.getRobot().getId());
        response.setMissionId(reservation.getMissionId());
        response.setStatus(reservation.getStatus());
        response.setReservedAt(reservation.getReservedAt());
        response.setExpiresAt(reservation.getExpiresAt());
        response.setConfirmedAt(reservation.getConfirmedAt());
        response.setReleasedAt(reservation.getReleasedAt());

        return response;
    }

    private String createRequestHash(ReserveRobotRequest request) {

        String requestData = request.getRobotId()
                + "|" + request.getMissionId();

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    requestData.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", exception);
        }
    }
}
