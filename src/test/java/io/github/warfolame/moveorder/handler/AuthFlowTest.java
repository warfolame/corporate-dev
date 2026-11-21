package io.github.warfolame.moveorder.handler;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.warfolame.moveorder.config.Application;
import io.github.warfolame.moveorder.config.TokenService;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import io.github.warfolame.moveorder.persistence.MoveOrderJdbc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * The whole application on PostgreSQL with real logins and real tokens: the marker's path
 * without a browser. Nothing is mocked; every request goes through the filter chain.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthFlowTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String PATH = "/api/v1/move-orders";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MoveOrderJdbc orders;

    @Autowired
    private JdbcTemplate jdbc;

    /** The real encoder with the dev secret, to build tokens login would never issue. */
    @Autowired
    private JwtEncoder encoder;

    private MoveOrder inProgress;

    @BeforeEach
    void cleanDatabase() throws IOException {
        jdbc.execute("DROP TABLE IF EXISTS move_order");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/schema.sql")));
        inProgress = MoveOrder.requested(MoveOrderId.newId(), new MoveOrderNumber("MO-2026-0042"),
                        "2-room flat: Abay 10 -> Dostyk 5")
                .payDeposit()
                .withStatus(MoveOrderStatus.IN_PROGRESS);
        orders.insert(inProgress);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post(LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"%s\", \"password\": \"%s\"}".formatted(username, password)));
    }

    private String tokenOf(String username, String password) throws Exception {
        String body = login(username, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private MoveOrderStatus storedStatus(MoveOrder order) {
        return orders.findById(order.id()).orElseThrow().status();
    }

    // ---------------------------------------------------------------- login

    @Test
    void loginReturnsABearerTokenForFifteenMinutes() throws Exception {
        login("aliya", "dispatcher-dev")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.accessToken").value(containsString(".")))
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(content().string(not(containsString("dispatcher-dev"))));
    }

    @Test
    void theTokenNamesTheUserTheRoleAndOurProduct() throws Exception {
        String[] parts = tokenOf("nurlan", "manager-dev").split("\\.");
        String header = decode(parts[0]);
        String payload = decode(parts[1]);

        assertEquals(3, parts.length);
        assertEquals("{\"alg\":\"HS256\"}", header);
        assertEquals("move-orders", JsonPath.read(payload, "$.iss"));
        assertEquals("nurlan", JsonPath.read(payload, "$.sub"));
        assertEquals(List.of("MANAGER"), JsonPath.read(payload, "$.roles"));
        int iat = JsonPath.read(payload, "$.iat");
        int exp = JsonPath.read(payload, "$.exp");
        assertEquals(900, exp - iat);
        assertFalse(payload.contains("manager-dev"));
    }

    /** One message for both: a different text for "no such user" tells an attacker which names exist. */
    @Test
    void unknownUserAndWrongPasswordAreTheSame401() throws Exception {
        login("ghost", "dispatcher-dev")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("bad username or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        login("aliya", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("bad username or password"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void blankUsernameOrPasswordIs400WithTheFieldNames() throws Exception {
        login("", " ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("password must not be blank; username must not be blank"));
    }

    @Test
    void thePasswordIsNotInTheLog(CapturedOutput output) throws Exception {
        login("aliya", "dispatcher-dev").andExpect(status().isOk());
        login("aliya", "typed-wrong-password").andExpect(status().isUnauthorized());

        assertFalse(output.getAll().contains("dispatcher-dev"));
        assertFalse(output.getAll().contains("typed-wrong-password"));
    }

    // ---------------------------------------------------------------- who am I

    @Test
    void meShowsTheUserAndAuthoritiesFromTheToken() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", bearer(tokenOf("aliya", "dispatcher-dev"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aliya"))
                .andExpect(jsonPath("$.authorities", hasSize(1)))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_DISPATCHER"));
    }

    // ---------------------------------------------------------------- the locked action

    @Test
    void dispatcherIs403AndTheRowStays() throws Exception {
        String aliya = tokenOf("aliya", "dispatcher-dev");

        mvc.perform(post(PATH + "/" + inProgress.id().value() + "/complete")
                        .header("Authorization", bearer(aliya))
                        .header("X-Correlation-Id", "desk-4422"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-Id", "desk-4422"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("not allowed for your role"))
                .andExpect(jsonPath("$.correlationId").value("desk-4422"));

        assertEquals(MoveOrderStatus.IN_PROGRESS, storedStatus(inProgress));
    }

    @Test
    void managerCompletesAndTheLogNamesHimWithoutTheToken(CapturedOutput output) throws Exception {
        String nurlan = tokenOf("nurlan", "manager-dev");

        mvc.perform(post(PATH + "/" + inProgress.id().value() + "/complete").header("Authorization", bearer(nurlan)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.businessKey").value("MO-2026-0042"));

        assertEquals(MoveOrderStatus.COMPLETED, storedStatus(inProgress));
        assertTrue(output.getAll().contains("IN_PROGRESS -> COMPLETED by nurlan"));
        assertFalse(output.getAll().contains(nurlan));
    }

    @Test
    void completingTwiceIsTheDomainErrorAndNot200() throws Exception {
        String nurlan = tokenOf("nurlan", "manager-dev");
        String complete = PATH + "/" + inProgress.id().value() + "/complete";
        mvc.perform(post(complete).header("Authorization", bearer(nurlan))).andExpect(status().isOk());

        mvc.perform(post(complete).header("Authorization", bearer(nurlan)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Move order cannot go from COMPLETED to COMPLETED"));

        assertEquals(MoveOrderStatus.COMPLETED, storedStatus(inProgress));
    }

    /** Lab 1's forbidden row, now over HTTP with the right role: the policy still says no. */
    @Test
    void managerCannotCompleteARequestedOrder() throws Exception {
        MoveOrder requested = MoveOrder.requested(MoveOrderId.newId(), new MoveOrderNumber("MO-2026-0043"), "Office");
        orders.insert(requested);

        mvc.perform(post(PATH + "/" + requested.id().value() + "/complete")
                        .header("Authorization", bearer(tokenOf("nurlan", "manager-dev"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

        assertEquals(MoveOrderStatus.REQUESTED, storedStatus(requested));
    }

    @Test
    void completingAnUnknownOrderIs404() throws Exception {
        mvc.perform(post(PATH + "/" + UUID.randomUUID() + "/complete")
                        .header("Authorization", bearer(tokenOf("nurlan", "manager-dev"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---------------------------------------------------------------- Lab 4, now with a token

    @Test
    void lab4StatusesHoldWithARealToken() throws Exception {
        String aliya = bearer(tokenOf("aliya", "dispatcher-dev"));
        String body = "{\"businessKey\": \"MO-2026-0050\", \"title\": \"Studio: Satpayev 1 -> Tole bi 2\"}";

        String location = mvc.perform(post(PATH).header("Authorization", aliya)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location).header("Authorization", aliya))
                .andExpect(status().isOk());
        mvc.perform(post(PATH).header("Authorization", aliya).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));
        mvc.perform(post(PATH).header("Authorization", aliya).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessKey\": \"MO-2026-0051\", \"title\": \" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
        mvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", aliya))
                .andExpect(status().isNotFound());

        assertEquals(1, orders.count(new MoveOrderNumber("MO-2026-0050")));
        assertEquals(0, orders.count(new MoveOrderNumber("MO-2026-0051")));
    }

    // ---------------------------------------------------------------- tokens that fail as 401

    @Test
    void noTokenIs401AndCreatesNothing() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessKey\": \"MO-2026-0060\", \"title\": \"Office\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("valid token required"))
                .andExpect(header().exists("X-Correlation-Id"));

        assertEquals(0, orders.count(new MoveOrderNumber("MO-2026-0060")));
    }

    @Test
    void notAJwtIs401() throws Exception {
        expect401(bearer("hello"));
    }

    @Test
    void anEditedRoleIs401() throws Exception {
        String[] parts = tokenOf("aliya", "dispatcher-dev").split("\\.");
        String payload = decode(parts[1]).replace("DISPATCHER", "MANAGER");
        String edited = parts[0] + "." + encode(payload) + "." + parts[2];

        mvc.perform(post(PATH + "/" + inProgress.id().value() + "/complete").header("Authorization", bearer(edited)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        assertEquals(MoveOrderStatus.IN_PROGRESS, storedStatus(inProgress));
    }

    @Test
    void anExpiredTokenIs401() throws Exception {
        expect401(bearer(sign(TokenService.ISSUER, Instant.now().minusSeconds(20 * 60), Instant.now().minusSeconds(5 * 60))));
    }

    @Test
    void aTokenFromAnotherProductIs401() throws Exception {
        expect401(bearer(sign("helpdesk", Instant.now(), Instant.now().plusSeconds(600))));
    }

    /** HTTP Basic is not how clients call this API: the password goes only to the login. */
    @Test
    void basicAuthIsNotACredential() throws Exception {
        String basic = Base64.getEncoder().encodeToString("aliya:dispatcher-dev".getBytes(StandardCharsets.UTF_8));
        expect401("Basic " + basic);
    }

    private void expect401(String authorization) throws Exception {
        mvc.perform(get("/api/me").header("Authorization", authorization))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("valid token required"));
    }

    // ---------------------------------------------------------------- open rows

    @Test
    void healthTheContractAndThePageAreOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Move Order Tracker")))
                .andExpect(content().string(containsString("DISPATCHER")))
                .andExpect(content().string(containsString("type=\"password\"")))
                .andExpect(content().string(not(containsString("Please sign in"))));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/app.css")).andExpect(status().isOk());
    }

    @Test
    void theContractNamesTheSecurityStatuses() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/move-orders/{id}/complete'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/move-orders/{id}/complete'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/move-orders/{id}/complete'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/me'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.components.securitySchemes['bearer-jwt'].scheme").value("bearer"));
    }

    /** Spring's generated login and logout pages are off: no formLogin, no /logout endpoint. */
    @Test
    void springsOwnLoginPageIsOff() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isUnauthorized());
        mvc.perform(post("/logout")).andExpect(status().isUnauthorized());
    }

    private String sign(String issuer, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("nurlan")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of("MANAGER"))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    private static String encode(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
