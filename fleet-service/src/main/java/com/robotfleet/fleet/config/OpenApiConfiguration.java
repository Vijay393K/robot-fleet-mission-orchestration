package com.robotfleet.fleet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI fleetServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Robot Fleet Service API")
                .version("1.0.0")
                .description("Robot registration, status, telemetry, heartbeat, availability, and reservation APIs."));
    }
}