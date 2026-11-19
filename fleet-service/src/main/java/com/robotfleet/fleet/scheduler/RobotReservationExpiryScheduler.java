
package com.robotfleet.fleet.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.robotfleet.fleet.service.RobotReservationService;

@Component
public class RobotReservationExpiryScheduler {

    private final RobotReservationService reservationService;

    public RobotReservationExpiryScheduler(
            RobotReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Scheduled(fixedDelay = 10000)
    public void expireReservations() {
        reservationService.expireReservations();
    }
}
