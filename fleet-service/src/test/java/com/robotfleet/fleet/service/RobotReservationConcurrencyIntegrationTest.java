package com.robotfleet.fleet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.robotfleet.fleet.entity.Robot;
import com.robotfleet.fleet.enums.ReservationStatus;
import com.robotfleet.fleet.enums.RobotStatus;
import com.robotfleet.fleet.enums.RobotType;
import com.robotfleet.fleet.repository.RobotRepository;
import com.robotfleet.fleet.repository.RobotReservationRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RobotReservationConcurrencyIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RobotReservationService reservationService;

    @Autowired
    private RobotRepository robotRepository;

    @Autowired
    private RobotReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void onlyOneMissionCanReserveTheSameRobotConcurrently() throws Exception {
        UUID robotId = UUID.randomUUID();
        UUID firstMissionId = UUID.randomUUID();
        UUID secondMissionId = UUID.randomUUID();

        Robot robot = new Robot(
                robotId,
                "Reservation-Concurrency-Test",
                RobotType.CARRIER,
                90,
                10.0,
                20.0,
                RobotStatus.IDLE,
                Instant.now(),
                null,
                null);
        robotRepository.saveAndFlush(robot);

        try {
            List<Attempt> attempts = runConcurrentReservations(
                    robotId,
                    firstMissionId,
                    secondMissionId);

            List<Attempt> winners = attempts.stream()
                    .filter(attempt -> attempt.reservationId() != null)
                    .toList();

            assertEquals(1, winners.size(), "Exactly one mission should win");

            var activeReservations =
                    reservationRepository.findByRobot_IdAndStatusIn(
                            robotId,
                            List.of(
                                    ReservationStatus.RESERVED,
                                    ReservationStatus.CONFIRMED));

            assertEquals(1, activeReservations.size());

            var storedReservation = activeReservations.getFirst();
            var winningAttempt = winners.getFirst();

            assertEquals(winningAttempt.missionId(),
                    storedReservation.getMissionId());
            assertEquals(winningAttempt.reservationId(),
                    storedReservation.getId());
            assertEquals(ReservationStatus.RESERVED,
                    storedReservation.getStatus());
        } finally {
            jdbc.update(
                    "DELETE FROM robot_reservations WHERE robot_id = ?",
                    robotId);
            robotRepository.deleteById(robotId);
        }
    }

    private List<Attempt> runConcurrentReservations(
            UUID robotId,
            UUID firstMissionId,
            UUID secondMissionId) throws Exception {

        CyclicBarrier startTogether = new CyclicBarrier(2);

        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Attempt> first = workers.submit(
                    () -> attemptReservation(
                            startTogether, robotId, firstMissionId));
            Future<Attempt> second = workers.submit(
                    () -> attemptReservation(
                            startTogether, robotId, secondMissionId));

            return List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));
        }
    }

    private Attempt attemptReservation(
            CyclicBarrier startTogether,
            UUID robotId,
            UUID missionId) throws Exception {

        String idempotencyKey = UUID.randomUUID().toString();
        String requestHash = robotId.toString().replace("-", "")
                + missionId.toString().replace("-", "");

        startTogether.await(10, TimeUnit.SECONDS);

        try {
            var reservation = reservationService.reserveRobot(
                    robotId,
                    missionId,
                    "RESERVE_ROBOT",
                    idempotencyKey,
                    requestHash);

            assertNotNull(reservation.getId());
            return new Attempt(missionId, reservation.getId());
        } catch (ResponseStatusException exception) {
            assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
            assertEquals(
                    "Robot already has an active reservation",
                    exception.getReason());
            return new Attempt(missionId, null);
        }
    }

    private record Attempt(UUID missionId, UUID reservationId) {
    }
}