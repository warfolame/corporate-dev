package io.github.warfolame.moveorder.handler;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.warfolame.moveorder.config.Application;
import io.github.warfolame.moveorder.config.MoveOrderService;
import io.github.warfolame.moveorder.config.SecurityConfig;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import io.github.warfolame.moveorder.domain.TransitionNotAllowed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.servlet.autoconfigure.actuate.web.ServletManagementContextAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The rows of the README rules table, locked by the real filter chain ({@link SecurityConfig}).
 * Web slice: the service is a mock, no database. {@code jwt()} stands in for a checked token with
 * the given user and role; no {@code jwt()} means no token. Health is the real actuator endpoint.
 */
@WebMvcTest(controllers = {MoveOrderController.class, MeController.class})
@ContextConfiguration(classes = Application.class)
@Import(SecurityConfig.class)
@ImportAutoConfiguration({
        EndpointAutoConfiguration.class,
        WebEndpointAutoConfiguration.class,
        ManagementContextAutoConfiguration.class,
        ServletManagementContextAutoConfiguration.class,
        HealthContributorAutoConfiguration.class,
        HealthContributorRegistryAutoConfiguration.class,
        HealthEndpointAutoConfiguration.class})
class MoveOrderSecurityTest {

    private static final String PATH = "/api/v1/move-orders";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MoveOrderService service;

    /** The chain needs a decoder bean; jwt() never calls it. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final MoveOrder inProgress = MoveOrder.requested(
            MoveOrderId.newId(), new MoveOrderNumber("MO-2026-0042"), "2-room flat: Abay 10 -> Dostyk 5")
            .payDeposit()
            .withStatus(MoveOrderStatus.IN_PROGRESS);

    private static RequestPostProcessor dispatcher() {
        return jwt().jwt(token -> token.subject("aliya"))
                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"));
    }

    private static RequestPostProcessor manager() {
        return jwt().jwt(token -> token.subject("nurlan"))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private MockHttpServletRequestBuilder complete(MoveOrder order) {
        return post(PATH + "/" + order.id().value() + "/complete");
    }

    // ---- no token: 401, nothing behind the chain runs

    @Test
    void readWithoutATokenIs401AndTheServiceIsNotCalled() throws Exception {
        mvc.perform(get(PATH + "/" + inProgress.id().value()).header("X-Correlation-Id", "desk-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("valid token required"))
                .andExpect(jsonPath("$.correlationId").value("desk-1"))
                .andExpect(jsonPath("$.*", hasSize(3)));

        verifyNoInteractions(service);
    }

    @Test
    void createWithoutATokenIs401Not201() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessKey\": \"MO-2026-0042\", \"title\": \"Office\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    @Test
    void completeWithoutATokenIs401AndTheServiceIsNotCalled() throws Exception {
        mvc.perform(complete(inProgress))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").value(not("")));

        verifyNoInteractions(service);
    }

    // ---- junior role on the locked verb: 403, the service never runs

    @Test
    void dispatcherOnTheLockedVerbIs403AndTheServiceIsNotCalled() throws Exception {
        mvc.perform(complete(inProgress).with(dispatcher()).header("X-Correlation-Id", "desk-2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("not allowed for your role"))
                .andExpect(jsonPath("$.correlationId").value("desk-2"))
                .andExpect(jsonPath("$.*", hasSize(3)));

        verifyNoInteractions(service);
    }

    /** The caller does not get to type the role: a body saying MANAGER changes nothing. */
    @Test
    void roleOrActorInTheBodyIsIgnored() throws Exception {
        mvc.perform(complete(inProgress).with(dispatcher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\": \"nurlan\", \"role\": \"MANAGER\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    // ---- senior role: allowed, and the actor is the token's user

    @Test
    void managerCompletesAndTheActorIsTheTokensUser() throws Exception {
        when(service.complete(inProgress.id(), "nurlan")).thenReturn(inProgress.withStatus(MoveOrderStatus.COMPLETED));

        mvc.perform(complete(inProgress).with(manager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\": \"somebody-else\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.businessKey").value("MO-2026-0042"));

        verify(service).complete(inProgress.id(), "nurlan");
    }

    @Test
    void managerOnAnOrderThePolicyRefusesGetsTheDomainError() throws Exception {
        when(service.complete(any(), anyString()))
                .thenThrow(new TransitionNotAllowed("Move order cannot go from REQUESTED to COMPLETED"));

        mvc.perform(complete(inProgress).with(manager()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"))
                .andExpect(jsonPath("$.message").value("Move order cannot go from REQUESTED to COMPLETED"));
    }

    // ---- either role may create and read

    @Test
    void dispatcherMayRead() throws Exception {
        when(service.get(inProgress.id())).thenReturn(inProgress);

        mvc.perform(get(PATH + "/" + inProgress.id().value()).with(dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void managerMayRead() throws Exception {
        when(service.get(inProgress.id())).thenReturn(inProgress);

        mvc.perform(get(PATH + "/" + inProgress.id().value()).with(manager()))
                .andExpect(status().isOk());
    }

    /** A valid token with neither of our roles is a 403, not a 200. */
    @Test
    void aTokenWithoutOurRolesIs403() throws Exception {
        mvc.perform(get(PATH + "/" + inProgress.id().value())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(service);
    }

    // ---- who am I

    @Test
    void meReturnsTheUserAndRoleFromTheToken() throws Exception {
        mvc.perform(get("/api/me").with(dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("aliya"))
                .andExpect(jsonPath("$.authorities", hasSize(1)))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_DISPATCHER"));
    }

    @Test
    void meWithoutATokenIs401() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    // ---- open rows and the last row

    @Test
    void healthIsOpenWithoutAToken() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void anythingElseIs401WithoutATokenAnd403WithOne() throws Exception {
        mvc.perform(get("/internal/orders"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/orders").with(manager()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
