package com.forumx.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Binds {@code app.cors.*} configuration properties.
 *
 * <p>Set {@code CORS_ALLOWED_ORIGINS} environment variable to a comma-separated list of
 * allowed origins. For local development this should be {@code http://localhost:3000}.
 * For production, set to the deployed Azure Static Web Apps URL.</p>
 *
 * <p>Example application.yml binding:</p>
 * <pre>
 *   app:
 *     cors:
 *       allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
 * </pre>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /**
     * Comma-separated list of allowed CORS origins.
     * Populated from {@code CORS_ALLOWED_ORIGINS} environment variable via application.yml.
     */
    private List<String> allowedOrigins = List.of("http://localhost:3000");
}
