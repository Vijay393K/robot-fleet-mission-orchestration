package com.robotfleet.mission.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotReservationResponse;
import com.robotfleet.mission.enums.AssignmentOperationStatus;
import com.robotfleet.mission.service.MissionAssignmentFinalizationService.FinalizationResult;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MissionConfirmationProcessorTest {

    @Mock
    private FleetServiceClient fleetServiceClient;

    @Mock
    private MissionAssignmentFinalizationService finalizationService;

    @Mock
    private MissionAssignmentOutcomeService outcomeService;

    private MissionConfirmationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MissionConfirmationProcessor(
                fleetServiceClient,
                finalizationService,
                outcomeService
        );
    }

    @Test
    void shouldFinalizePersistedReservationAfterRecoveryClaim() {
        UUID operationId = UUID.randomUUID();
        UUID missionId = UUID.randomUUID();
        UUID robotId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID leaseToken = UUID.randomUUID();

        OperationClaim claim = new OperationClaim(
                operationId,
                missionId,
                robotId,
                1L,
                AssignmentOperationStatus.CONFIRM_PENDING,
                reservationId,
                Instant.now().plusSeconds(60),
                leaseToken,
                2L
        );

        FleetRobotReservationResponse confirmed =
                new FleetRobotReservationResponse(
                        reservationId,
                        robotId,
                        missionId,
                        FleetRobotReservationResponse.ReservationStatus.CONFIRMED,
                        Instant.now().minusSeconds(5),
                        Instant.now().plusSeconds(60),
                        Instant.now(),
                        null
                );

        when(fleetServiceClient.confirmReservation(reservationId))
                .thenReturn(confirmed);
        when(finalizationService.finalizeConfirmedReservation(
                claim,
                confirmed
        )).thenReturn(FinalizationResult.ASSIGNED);

        processor.processConfirm(claim);

        verify(fleetServiceClient).confirmReservation(reservationId);
        verify(finalizationService).finalizeConfirmedReservation(
                claim,
                confirmed
        );
        verifyNoInteractions(outcomeService);
    }

    @Test
    void shouldScheduleRetryWhenFleetHasNotConfirmedReservationYet() {
        UUID reservationId = UUID.randomUUID();
        OperationClaim claim = new OperationClaim(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                AssignmentOperationStatus.CONFIRM_PENDING,
                reservationId,
                Instant.now().plusSeconds(60),
                UUID.randomUUID(),
                1L
        );

        FleetRobotReservationResponse stillReserved =
                new FleetRobotReservationResponse(
                        reservationId,
                        claim.robotId(),
                        claim.missionId(),
                        FleetRobotReservationResponse.ReservationStatus.RESERVED,
                        Instant.now().minusSeconds(5),
                        Instant.now().plusSeconds(60),
                        null,
                        null
                );

        when(fleetServiceClient.confirmReservation(reservationId))
                .thenReturn(stillReserved);
        when(outcomeService.scheduleRetry(
                org.mockito.ArgumentMatchers.eq(claim),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq("CONFIRMATION_NOT_COMPLETED"),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(true);

        processor.processConfirm(claim);

        verify(fleetServiceClient).confirmReservation(reservationId);
        verify(outcomeService).scheduleRetry(
                org.mockito.ArgumentMatchers.eq(claim),
                org.mockito.ArgumentMatchers.any(Instant.class),
                org.mockito.ArgumentMatchers.eq("CONFIRMATION_NOT_COMPLETED"),
                org.mockito.ArgumentMatchers.anyString()
        );
        verifyNoInteractions(finalizationService);
    }
}
