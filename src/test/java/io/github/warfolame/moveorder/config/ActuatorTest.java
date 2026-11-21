package io.github.warfolame.moveorder.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Only health and info are exposed, and health tells nothing about the datasource.
 * Both stay open without a token; every other actuator path is closed by the filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsUpAndShowsNoDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void infoCarriesTheProductName() throws Exception {
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("Move Order Tracker"));
    }

    /** Not exposed (Lab 4), and since Lab 5 the chain's denyAll stops them before that: 401 without a token. */
    @ParameterizedTest(name = "/actuator/{0} without a token is 401")
    @ValueSource(strings = {"env", "beans", "heapdump", "configprops", "metrics"})
    void dangerousEndpointsAreClosedWithoutAToken(String endpoint) throws Exception {
        mvc.perform(get("/actuator/" + endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    /** Not a row of the rules table: even a manager gets 403. */
    @ParameterizedTest(name = "/actuator/{0} with a manager token is 403")
    @ValueSource(strings = {"env", "beans", "heapdump", "configprops", "metrics"})
    void dangerousEndpointsAreClosedForEveryRole(String endpoint) throws Exception {
        mvc.perform(get("/actuator/" + endpoint)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shutdownIsClosed() throws Exception {
        mvc.perform(post("/actuator/shutdown"))
                .andExpect(status().isUnauthorized());
    }
}
