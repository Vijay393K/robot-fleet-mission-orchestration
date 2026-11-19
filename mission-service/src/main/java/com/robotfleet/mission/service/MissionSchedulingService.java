
package com.robotfleet.mission.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.robotfleet.mission.client.FleetServiceClient;
import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse;
import com.robotfleet.mission.dto.FleetRobotPageResponse.RobotSummary;
import com.robotfleet.mission.dto.MissionAssignmentRequest;
import com.robotfleet.mission.entity.Mission;
import com.robotfleet.mission.enums.MissionPriority;
import com.robotfleet.mission.enums.RobotType;
import com.robotfleet.mission.repository.MissionRepository;

@Service
public class MissionSchedulingService {

    private static final Logger log =
            LoggerFactory.getLogger(MissionSchedulingService.class);

    private static final int MINIMUM_BATTERY = 25;
    private static final int ROBOT_PAGE_SIZE = 100;
    private static final int MISSION_PAGE_SIZE = 100;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 30;

    private final MissionRepository missionRepository;
    private final FleetServiceClient fleetServiceClient;
    private final MissionAssignmentIntentService assignmentIntentService;

    private final boolean automaticSchedulingEnabled;
    private final int maxAssignmentsPerRun;
    private final int maxMissionsExaminedPerRun;

    public MissionSchedulingService(
            MissionRepository missionRepository,
            FleetServiceClient fleetServiceClient,
            MissionAssignmentIntentService assignmentIntentService,
            @Value("${mission.scheduler.enabled:false}")
            boolean automaticSchedulingEnabled,
            @Value("${mission.scheduler.max-assignments-per-run:1}")
            int maxAssignmentsPerRun,
            @Value("${mission.scheduler.max-missions-examined-per-run:100}")
            int maxMissionsExaminedPerRun) {

        if (maxAssignmentsPerRun < 1 || maxAssignmentsPerRun > 20) {
            throw new IllegalArgumentException(
                    "Maximum assignments per run must be between 1 and 20"
            );
        }

        if (maxMissionsExaminedPerRun < 1
                || maxMissionsExaminedPerRun > 1000) {
            throw new IllegalArgumentException(
                    "Maximum missions examined must be between 1 and 1000"
            );
        }

        this.missionRepository = missionRepository;
        this.fleetServiceClient = fleetServiceClient;
        this.assignmentIntentService = assignmentIntentService;
        this.automaticSchedulingEnabled = automaticSchedulingEnabled;
        this.maxAssignmentsPerRun = maxAssignmentsPerRun;
        this.maxMissionsExaminedPerRun = maxMissionsExaminedPerRun;
    }

    @Scheduled(
            initialDelayString =
                    "${mission.scheduler.initial-delay-ms:10000}",
            fixedDelayString =
                    "${mission.scheduler.delay-ms:10000}"
    )
    public void executeAutomatically() {

        if (!automaticSchedulingEnabled) {
            return;
        }

        try {
            SchedulingResult result = executeScheduling();

            if (result.intentsCreated() > 0) {
                log.info(
                        "Scheduler created {} assignment intents",
                        result.intentsCreated()
                );
            }

        } catch (RuntimeException exception) {
            log.error("Automatic scheduling failed", exception);
        }
    }

    public SchedulingResult executeScheduling() {

        Map<RobotType, List<RobotSummary>> robotsByType =
                new EnumMap<>(RobotType.class);

        Set<UUID> selectedRobots = new HashSet<>();
        Set<UUID> unavailableRobots = new HashSet<>();

        List<AssignmentIntent> assignments = new ArrayList<>();

        int missionsExamined = 0;
        int rank = -1;

        Instant createdAt = Instant.EPOCH;
        UUID lastMissionId = new UUID(0L, 0L);

        while (assignments.size() < maxAssignmentsPerRun
                && missionsExamined < maxMissionsExaminedPerRun) {

            int batchSize = Math.min(
                    MISSION_PAGE_SIZE,
                    maxMissionsExaminedPerRun - missionsExamined
            );

            List<Mission> missions =
                    missionRepository.findSchedulableAfter(
                            rank,
                            createdAt,
                            lastMissionId,
                            batchSize
                    );

            if (missions.isEmpty()) {
                break;
            }

            for (Mission mission : missions) {

                rank = priorityRank(mission.getPriority());
                createdAt = mission.getCreatedAt();
                lastMissionId = mission.getId();

                missionsExamined++;

                List<RobotSummary> robots =
                        robotsByType.computeIfAbsent(
                                mission.getRequiredRobotType(),
                                this::loadIdleRobots
                        );

                List<RobotSummary> candidates = robots.stream()
                        .filter(robot ->
                                robot.type()
                                        == mission.getRequiredRobotType()
                        )
                        .filter(robot ->
                                !selectedRobots.contains(robot.id())
                        )
                        .filter(robot ->
                                !unavailableRobots.contains(robot.id())
                        )
                        .filter(this::isEligibleSnapshot)
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                (RobotSummary robot) ->
                                                        distanceSquared(
                                                                robot,
                                                                mission
                                                        )
                                        )
                                        .thenComparing(RobotSummary::id)
                        )
                        .toList();

                for (RobotSummary robot : candidates) {

                    FleetRobotAvailabilityResponse availability =
                            fleetServiceClient.getRobotAvailability(
                                    robot.id()
                            );

                    if (!availability.available()
                            || !robot.id().equals(
                                    availability.robotId()
                            )
                            || availability.robotType()
                                    != mission.getRequiredRobotType()
                            || availability.batteryPercentage()
                                    < MINIMUM_BATTERY) {

                        unavailableRobots.add(robot.id());
                        continue;
                    }

                    try {
                        UUID operationId =
                                assignmentIntentService.createAssignmentIntent(
                                        mission.getId(),
                                        new MissionAssignmentRequest(
                                                robot.id(),
                                                "SCHEDULER",
                                                "Automatic mission scheduling"
                                        ),
                                        "scheduler-" + UUID.randomUUID()
                                );

                        selectedRobots.add(robot.id());

                        assignments.add(
                                new AssignmentIntent(
                                        mission.getId(),
                                        robot.id(),
                                        operationId
                                )
                        );

                    } catch (ResponseStatusException exception) {

                        if (exception.getStatusCode().value()
                                != HttpStatus.CONFLICT.value()) {
                            throw exception;
                        }

                        log.debug(
                                "Concurrent scheduling conflict for mission {}",
                                mission.getId()
                        );
                    }

                    break;
                }

                if (assignments.size() >= maxAssignmentsPerRun
                        || missionsExamined >= maxMissionsExaminedPerRun) {
                    break;
                }
            }
        }

        return new SchedulingResult(
                missionsExamined,
                assignments.size(),
                List.copyOf(assignments)
        );
    }

    private List<RobotSummary> loadIdleRobots(RobotType type) {

        List<RobotSummary> robots = new ArrayList<>();

        for (int page = 0; ; page++) {

            FleetRobotPageResponse response =
                    fleetServiceClient.getIdleRobots(
                            type,
                            MINIMUM_BATTERY,
                            page,
                            ROBOT_PAGE_SIZE
                    );

            if (response.content() == null) {
                throw new IllegalStateException(
                        "Fleet Service returned an invalid robot page"
                );
            }

            robots.addAll(response.content());

            if (page + 1 >= response.totalPages()) {
                break;
            }
        }

        return List.copyOf(robots);
    }

    private boolean isEligibleSnapshot(RobotSummary robot) {

        Instant now = Instant.now();
        Instant heartbeat = robot.lastHeartbeat();

        return "IDLE".equals(robot.status())
                && robot.batteryPercentage() >= MINIMUM_BATTERY
                && !robot.safetyHold()
                && robot.currentMissionId() == null
                && heartbeat != null
                && !heartbeat.isAfter(now.plusSeconds(5))
                && !heartbeat.isBefore(
                        now.minusSeconds(HEARTBEAT_TIMEOUT_SECONDS)
                );
    }

    private double distanceSquared(
            RobotSummary robot,
            Mission mission) {

        double dx = robot.locationX() - mission.getPickupX();
        double dy = robot.locationY() - mission.getPickupY();

        return dx * dx + dy * dy;
    }

    private int priorityRank(MissionPriority priority) {

        return switch (priority) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case MEDIUM -> 2;
            case LOW -> 3;
        };
    }

    public record AssignmentIntent(
            UUID missionId,
            UUID robotId,
            UUID operationId
    ) {
    }

    public record SchedulingResult(
            int missionsExamined,
            int intentsCreated,
            List<AssignmentIntent> assignments
    ) {
    }
}
