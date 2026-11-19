package com.robotfleet.mission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI missionServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Robot Mission Service API")
                        .version("1.0.0")
                        .description("Mission creation, scheduling, lifecycle, and execution events."));
    }
}