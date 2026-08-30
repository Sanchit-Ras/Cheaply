package com.cheaply.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * The bearer scheme is declared here but deliberately not applied globally.
     * Signup, login, refresh and search are open to anonymous callers, and a
     * global requirement would document them as needing a token and make the
     * Swagger UI attach one. Endpoints that do require authentication carry
     * {@code @SecurityRequirement} individually.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Cheaply V2 Backend API")
                        .version("2.0")
                        .description("Grocery price comparison across multiple Indian online stores. "
                                + "Prices are normalised to a common unit (per kg or per litre) and "
                                + "ranked cheapest-first within each unit.")
                        .contact(new Contact().name("Cheaply Engineering")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
