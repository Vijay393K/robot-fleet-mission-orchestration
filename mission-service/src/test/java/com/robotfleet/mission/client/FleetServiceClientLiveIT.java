
package com.robotfleet.mission.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.robotfleet.mission.dto.FleetRobotAvailabilityResponse;
import com.robotfleet.mission.enums.RobotType;

class FleetServiceClientLiveIT {

    private static final UUID TEST_ROBOT_ID = UUID.fromString(
            "c986dc8c-fec4-4bbf-9e39-068c4aead61f"
    );

    @Test
    void shouldRetrieveRobotAvailabilityFromFleetService() {

        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:8081")
                .build();

        FleetServiceClient fleetClient =
                new FleetServiceClient(restClient);

        FleetRobotAvailabilityResponse response =
                fleetClient.getRobotAvailability(TEST_ROBOT_ID);

        assertNotNull(response);
        assertEquals(TEST_ROBOT_ID, response.robotId());
        assertEquals(RobotType.PICKER, response.robotType());
        assertNotNull(response.location());
        assertNotNull(response.reasons());
    }
}
