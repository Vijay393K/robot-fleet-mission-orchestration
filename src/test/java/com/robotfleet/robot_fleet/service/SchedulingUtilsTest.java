package com.robotfleet.robot_fleet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SchedulingUtilsTest {

    @Test
    void shouldGiveCriticalHighestPriority() {
        assertEquals(1, SchedulingUtils.getPriorityRank("CRITICAL"));
    }

    @Test
    void shouldGiveHighSecondPriority() {
        assertEquals(2, SchedulingUtils.getPriorityRank("HIGH"));
    }

    @Test
    void shouldGiveMediumThirdPriority() {
        assertEquals(3, SchedulingUtils.getPriorityRank("MEDIUM"));
    }

    @Test
    void shouldGiveLowLowestPriority() {
        assertEquals(4, SchedulingUtils.getPriorityRank("LOW"));
    }

    @Test
    void shouldCalculateDistanceCorrectly() {
        double distance = SchedulingUtils.calculateDistance(0, 0, 3, 4);

        assertEquals(5.0, distance, 0.001);
    }

    @Test
    void shouldHandleLowercasePriority() {
        assertEquals(1, SchedulingUtils.getPriorityRank("critical"));
    }
}