package io.github.warfolame.moveorder.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * 401 and 403 from the filter chain, as our usual {@link ErrorBody}. These happen before any
 * controller, so {@code @RestControllerAdvice} never sees them, and Spring's default body is empty.
 *
 * <ul>
 *   <li>no {@code Authorization: Bearer}, not a JWT, wrong signature, expired, other issuer:
 *       401 {@code UNAUTHENTICATED} "valid token required"</li>
 *   <li>a valid token whose role the rule does not allow: 403 {@code FORBIDDEN} "not allowed for your role"</li>
 * </ul>
 *
 * The token itself is never logged and never echoed.
 */
public class SecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String TOKEN_REQUIRED = "valid token required";
    static final String NOT_ALLOWED = "not allowed for your role";

    private static final Logger log = LoggerFactory.getLogger(SecurityErrors.class);

    private final JsonMapper json;

    public SecurityErrors(JsonMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException e) throws IOException {
        log.info("401 {} {} correlationId={}", request.getMethod(), request.getRequestURI(),
                CorrelationId.of(request));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", TOKEN_REQUIRED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException e) throws IOException {
        log.info("403 {} {} for {} correlationId={}", request.getMethod(), request.getRequestURI(),
                request.getRemoteUser(), CorrelationId.of(request));
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", NOT_ALLOWED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
                       HttpStatus status, String code, String message) throws IOException {
        String correlationId = CorrelationId.of(request);
        response.setStatus(status.value());
        response.setHeader(CorrelationId.HEADER, correlationId);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), new ErrorBody(code, message, correlationId));
    }
}
