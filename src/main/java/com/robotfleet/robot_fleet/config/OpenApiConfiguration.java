package com.robotfleet.robot_fleet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI robotFleetOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Robot Fleet Mission Orchestration API")
                .version("1.0.0")
                .description("Robot fleet management, telemetry, mission scheduling, execution, and recovery APIs."));
    }
}