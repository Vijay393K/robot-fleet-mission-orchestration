package com.robotfleet.robot_fleet;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RobotOfflineScheduler {

    private final JdbcTemplate jdbcTemplate;

    public RobotOfflineScheduler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedRate = 10000)
    public void checkRobotHeartbeats() {

        String sql = """
            UPDATE robots
            SET status = 'OFFLINE'
            WHERE last_heartbeat IS NOT NULL
              AND last_heartbeat < NOW() - INTERVAL '30 seconds'
              AND status IN ('IDLE', 'CHARGING')
            """;

        int updatedRobots = jdbcTemplate.update(sql);

        if (updatedRobots > 0) {
            System.out.println(
                "Marked " + updatedRobots + " robot(s) OFFLINE."
            );
        }
    }
}