package com.tickethub.event.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI ticketHubOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketHub Event Service API")
                        .version("1.0")
                        .description("Event, venue, screen, seat, show and show-seat APIs. Authentication is handled by the API Gateway."));
    }
}
