
package com.robotfleet.mission.client;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse;
import com.robotfleet.mission.dto.FleetRobotReservationRequest;
import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.enums.RobotType;
import com.robotfleet.mission.exception.FleetServiceException;
import com.robotfleet.mission.exception.FleetServiceException.FailureType;
import com.robotfleet.mission.dto.FleetRobotStateResponse;
@Component
public class FleetServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(FleetServiceClient.class);

    private final RestClient restClient;

    public FleetServiceClient(
            @Qualifier("fleetRestClient") RestClient restClient) {

        this.restClient = Objects.requireNonNull(restClient);
    }

    public FleetRobotPageResponse getIdleRobots(
            RobotType robotType,
            int minBattery,
            int page,
            int size) {

        Objects.requireNonNull(robotType, "Robot type is required");

        if (minBattery < 0 || minBattery > 100) {
            throw new IllegalArgumentException(
                    "Minimum battery must be between 0 and 100");
        }

        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid robot pagination parameters");
        }

        return execute(
                "GET_IDLE_ROBOTS",
                false,
                () -> restClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/robots")
                                .queryParam("type", robotType.name())
                                .queryParam("status", "IDLE")
                                .queryParam("minBattery", minBattery)
                                .queryParam("page", page)
                                .queryParam("size", size)
                                .build())
                        .retrieve()
                        .body(FleetRobotPageResponse.class)
        );
    }
    public FleetRobotStateResponse getRobotState(UUID robotId) {

        Objects.requireNonNull(
                robotId,
                "Robot ID is required"
        );

        return execute(
                "GET_ROBOT_STATE",
                false,
                () -> restClient.get()
                        .uri(
                                "/api/v1/robots/{robotId}",
                                robotId
                        )
                        .retrieve()
                        .body(FleetRobotStateResponse.class)
        );
    }


    public FleetRobotAvailabilityResponse getRobotAvailability(
            UUID robotId) {

        Objects.requireNonNull(robotId, "Robot ID is required");

        return execute(
                "GET_ROBOT_AVAILABILITY",
                false,
                () -> restClient.get()
                        .uri("/api/v1/robots/{robotId}/availability",
                                robotId)
                        .retrieve()
                        .body(FleetRobotAvailabilityResponse.class)
        );
    }

    public FleetRobotReservationResponse reserveRobot(
            UUID robotId,
            UUID missionId,
            String idempotencyKey) {

        Objects.requireNonNull(robotId, "Robot ID is required");
        Objects.requireNonNull(missionId, "Mission ID is required");

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException(
                    "Idempotency key is required");
        }

        FleetRobotReservationRequest request =
                new FleetRobotReservationRequest(robotId, missionId);

        return execute(
                "RESERVE_ROBOT",
                true,
                () -> restClient.post()
                        .uri("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .body(request)
                        .retrieve()
                        .body(FleetRobotReservationResponse.class)
        );
    }

    public FleetRobotReservationResponse confirmReservation(
            UUID reservationId) {

        Objects.requireNonNull(
                reservationId, "Reservation ID is required");

        return execute(
                "CONFIRM_RESERVATION",
                true,
                () -> restClient.post()
                        .uri(
                                "/api/v1/reservations/{reservationId}/confirm",
                                reservationId)
                        .retrieve()
                        .body(FleetRobotReservationResponse.class)
        );
    }

    public FleetRobotReservationResponse releaseReservation(
            UUID reservationId) {

        Objects.requireNonNull(
                reservationId, "Reservation ID is required");

        return execute(
                "RELEASE_RESERVATION",
                true,
                () -> restClient.post()
                        .uri(
                                "/api/v1/reservations/{reservationId}/release",
                                reservationId)
                        .retrieve()
                        .body(FleetRobotReservationResponse.class)
        );
    }

    private <T> T execute(
            String operation,
            boolean stateChanging,
            Supplier<T> request) {

        try {
            T response = request.get();

            if (response == null) {
                log.error(
                        "Fleet Service returned an empty response. operation={}",
                        operation);

                throw new FleetServiceException(
                        operation,
                        FailureType.INVALID_RESPONSE,
                        null,
                        stateChanging,
                        "Fleet Service returned an empty response",
                        null
                );
            }

            return response;

        } catch (RestClientResponseException ex) {

            int status = ex.getStatusCode().value();

            boolean uncertain =
                    stateChanging && (
                            status >= 500
                            || status == 408
                            || status == 425
                            || status == 429
                    );

            FailureType failureType =
                    uncertain || status >= 500
                            ? FailureType.REMOTE_SERVICE_ERROR
                            : FailureType.REQUEST_REJECTED;

            log.warn(
                    "Fleet Service HTTP error. operation={}, status={}, outcomeUnknown={}",
                    operation,
                    status,
                    uncertain
            );

            throw new FleetServiceException(
                    operation,
                    failureType,
                    status,
                    uncertain,
                    "Fleet Service returned HTTP " + status,
                    ex
            );

        } catch (ResourceAccessException ex) {

            log.warn(
                    "Fleet Service communication failure. operation={}, outcomeUnknown={}",
                    operation,
                    stateChanging
            );

            throw new FleetServiceException(
                    operation,
                    FailureType.CONNECTION_FAILURE,
                    null,
                    stateChanging,
                    "Unable to communicate with Fleet Service",
                    ex
            );

        } catch (RestClientException ex) {

            log.error(
                    "Fleet Service response processing failed. operation={}",
                    operation,
                    ex
            );

            throw new FleetServiceException(
                    operation,
                    FailureType.INVALID_RESPONSE,
                    null,
                    stateChanging,
                    "Unable to process Fleet Service response",
                    ex
            );
        }
    }
}
