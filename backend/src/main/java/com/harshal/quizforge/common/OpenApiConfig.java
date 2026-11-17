package com.harshal.quizforge.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * API documentation: spec at /v3/api-docs, interactive UI at /swagger-ui.html.
 * To call protected endpoints from Swagger UI: log in via POST /api/auth/login,
 * copy "token", click "Authorize" and paste it.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI quizForgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("QuizForge API")
                        .version("v2")
                        .description("""
                                AI-powered assessment platform. Authentication uses a short-lived JWT access \
                                token (Authorization: Bearer) plus an HttpOnly refresh-token cookie. \
                                Errors are returned as RFC 7807 ProblemDetail JSON.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from /api/auth/login, /register or /refresh")))
                // Every endpoint requires the token unless it opts out (see AuthController).
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
