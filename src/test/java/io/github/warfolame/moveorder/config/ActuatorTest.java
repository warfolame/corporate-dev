package io.github.warfolame.moveorder.config;

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
import org.springframework.test.web.servlet.MockMvc;

/** Only health and info are exposed, and health tells nothing about the datasource. */
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

    @ParameterizedTest(name = "/actuator/{0} is not exposed")
    @ValueSource(strings = {"env", "beans", "heapdump", "configprops", "metrics"})
    void dangerousEndpointsAre404(String endpoint) throws Exception {
        mvc.perform(get("/actuator/" + endpoint))
                .andExpect(status().isNotFound());
    }

    @Test
    void shutdownIsNotExposed() throws Exception {
        mvc.perform(post("/actuator/shutdown"))
                .andExpect(status().isNotFound());
    }
}
