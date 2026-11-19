
package com.robotfleet.mission.scheduler;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.robotfleet.mission.repository.MissionAssignmentOperationRepository;
import com.robotfleet.mission.service.MissionRecoveryFinalizationService;
import com.robotfleet.mission.service.MissionRecoveryFinalizationService.RecoveryFinalizationResult;

@Component
public class MissionRecoveryFinalizationWorker {

    private static final Logger log =
            LoggerFactory.getLogger(
                    MissionRecoveryFinalizationWorker.class
            );

    private static final int BATCH_SIZE = 20;

    private static final UUID START_ID =
            new UUID(0L, 0L);

    private final MissionAssignmentOperationRepository
            operationRepository;

    private final MissionRecoveryFinalizationService
            finalizationService;

    // Cursor is a scanning optimization only.
    // PostgreSQL remains the source of truth.
    private UUID lastScannedId = START_ID;

    public MissionRecoveryFinalizationWorker(
            MissionAssignmentOperationRepository operationRepository,
            MissionRecoveryFinalizationService finalizationService) {

        this.operationRepository = operationRepository;
        this.finalizationService = finalizationService;
    }

    @Scheduled(
            initialDelayString =
                    "${mission.recovery.finalization.initial-delay-ms:10000}",
            fixedDelayString =
                    "${mission.recovery.finalization.delay-ms:5000}"
    )
    public void finalizeReleasedRecoveries() {

        List<UUID> operationIds;

        try {

            operationIds =
                    operationRepository
                            .findReleasedAssignmentsPendingFinalizationAfter(
                                    lastScannedId,
                                    BATCH_SIZE
                            );

        } catch (RuntimeException exception) {

            log.error(
                    "Unable to query released assignments "
                    + "requiring finalization",
                    exception
            );

            return;
        }

        // Restart the scan after reaching the end.
        // This also allows previously failed operations
        // to be discovered again.
        if (operationIds.isEmpty()) {
            lastScannedId = START_ID;
            return;
        }

        for (UUID operationId : operationIds) {

            try {

                RecoveryFinalizationResult result =
                        finalizationService
                                .finalizeReleasedAssignment(
                                        operationId
                                );

                switch (result) {

                    case FINALIZED -> log.info(
                            "Released assignment finalized. "
                            + "operationId={}",
                            operationId
                    );

                    case ALREADY_FINALIZED -> log.debug(
                            "Assignment already finalized. "
                            + "operationId={}",
                            operationId
                    );

                    case NO_ASSIGNMENT_TO_CLOSE -> log.debug(
                            "Released operation has no assignment "
                            + "requiring finalization. "
                            + "operationId={}",
                            operationId
                    );

                    case NOT_RELEASED -> log.warn(
                            "Assignment operation is not RELEASED. "
                            + "operationId={}",
                            operationId
                    );
                }

            } catch (RuntimeException exception) {

                log.error(
                        "Assignment finalization failed. "
                        + "operationId={}. "
                        + "The operation will remain eligible "
                        + "for a subsequent scan.",
                        operationId,
                        exception
                );

            } finally {

                // Advance even when one operation fails.
                // Failed operations are revisited when
                // the scan returns to START_ID.
                lastScannedId = operationId;
            }
        }
    }
}
