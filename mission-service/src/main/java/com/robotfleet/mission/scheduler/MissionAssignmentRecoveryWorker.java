
package com.robotfleet.mission.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.robotfleet.mission.service.MissionAssignmentLeaseService;
import com.robotfleet.mission.service.MissionAssignmentLeaseService.OperationClaim;
import com.robotfleet.mission.service.MissionConfirmationProcessor;
import com.robotfleet.mission.service.MissionReleaseProcessor;
import com.robotfleet.mission.service.MissionReservationProcessor;

@Component
public class MissionAssignmentRecoveryWorker {

    private static final Logger log =
            LoggerFactory.getLogger(
                    MissionAssignmentRecoveryWorker.class
            );

    private static final int MAX_OPERATIONS_PER_RUN = 10;

    private final MissionAssignmentLeaseService leaseService;
    private final MissionReservationProcessor reservationProcessor;
    private final MissionConfirmationProcessor confirmationProcessor;
    private final MissionReleaseProcessor releaseProcessor;

    public MissionAssignmentRecoveryWorker(
            MissionAssignmentLeaseService leaseService,
            MissionReservationProcessor reservationProcessor,
            MissionConfirmationProcessor confirmationProcessor,
            MissionReleaseProcessor releaseProcessor) {

        this.leaseService = leaseService;
        this.reservationProcessor = reservationProcessor;
        this.confirmationProcessor = confirmationProcessor;
        this.releaseProcessor = releaseProcessor;
    }

    @Scheduled(
            initialDelayString =
                    "${mission.assignment.recovery.initial-delay-ms:10000}",
            fixedDelayString =
                    "${mission.assignment.recovery.delay-ms:5000}"
    )
    public void recoverPendingAssignments() {

        try {

            for (int processed = 0;
                    processed < MAX_OPERATIONS_PER_RUN;
                    processed++) {

                // Claim only one operation at a time.
                // Each claim commits before any REST call.
                List<OperationClaim> claims =
                        leaseService.claimDueOperations(1);

                if (claims.isEmpty()) {
                    return;
                }

                OperationClaim claim = claims.get(0);

                try {
                    processOperation(claim);

                } catch (RuntimeException exception) {

                    log.error(
                            "Assignment recovery failed. "
                            + "operationId={}, missionId={}, "
                            + "robotId={}, status={}, attempt={}. "
                            + "Operation can be retried after lease expiry.",
                            claim.operationId(),
                            claim.missionId(),
                            claim.robotId(),
                            claim.status(),
                            claim.attemptCount(),
                            exception
                    );
                }
            }

        } catch (RuntimeException exception) {

            log.error(
                    "Unable to claim pending assignment operations. "
                    + "Recovery will be attempted on the next run.",
                    exception
            );
        }
    }

    private void processOperation(OperationClaim claim) {

        switch (claim.status()) {

            case RESERVE_PENDING -> {

                log.debug(
                        "Processing reservation. operationId={}",
                        claim.operationId()
                );

                reservationProcessor.processReserve(claim);
            }

            case CONFIRM_PENDING -> {

                log.debug(
                        "Processing confirmation. operationId={}",
                        claim.operationId()
                );

                confirmationProcessor.processConfirm(claim);
            }

            case RELEASE_PENDING -> {

                log.debug(
                        "Processing compensation. operationId={}",
                        claim.operationId()
                );

                releaseProcessor.processRelease(claim);
            }

            default -> throw new IllegalStateException(
                    "Unexpected operation status: " + claim.status()
            );
        }
    }
}
