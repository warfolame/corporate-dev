package io.github.warfolame.moveorder.handler;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Tells the contract that calls need {@code Authorization: Bearer <JWT>}. Swagger UI then shows
 * an "Authorize" button: paste the {@code accessToken} from {@code POST /api/v1/auth/login}.
 */
@Configuration
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    public static final String BEARER = "bearer-jwt";
}
