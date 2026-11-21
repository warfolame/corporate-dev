package io.github.warfolame.moveorder.handler;

import io.github.warfolame.moveorder.config.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only place a password is sent. {@link AuthenticationManager} checks the name and the
 * BCrypt hash; a wrong password or an unknown name throws, and {@link ApiErrorAdvice} answers
 * both with the same 401. On success {@link TokenService} signs a 15-minute token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authentication;
    private final TokenService tokens;

    public AuthController(AuthenticationManager authentication, TokenService tokens) {
        this.authentication = authentication;
        this.tokens = tokens;
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Log in with a username and password; get a 15-minute Bearer token")
    @ApiResponse(responseCode = "200", description = "accessToken, tokenType Bearer, expiresIn 900",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION: username or password is blank",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    @ApiResponse(responseCode = "401", description = "UNAUTHENTICATED: bad username or password (one message for both)",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication user = authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        String token = tokens.issue(user);
        log.info("token issued to {}, valid {} min", user.getName(), TokenService.LIFETIME.toMinutes());
        return new TokenResponse(token, "Bearer", TokenService.LIFETIME.toSeconds());
    }
}
