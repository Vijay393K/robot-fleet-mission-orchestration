
package com.robotfleet.mission.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class FleetRestClientConfig {

    @Bean
    public RestClient fleetRestClient(
            RestClient.Builder builder,
            @Value("${fleet.service.base-url}") String baseUrl,
            @Value("${fleet.service.connect-timeout:2s}") Duration connectTimeout,
            @Value("${fleet.service.read-timeout:5s}") Duration readTimeout) {

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
