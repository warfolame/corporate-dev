package io.github.warfolame.moveorder.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.warfolame.moveorder.config.Application;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.persistence.MoveOrderJdbc;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The whole app over HTTP against the real PostgreSQL: what Lab 3 proved for
 * {@code register}, now proved for {@code POST}. No transaction of its own.
 */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
class MoveOrderApiTest {

    private static final String PATH = "/api/v1/move-orders";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MoveOrderJdbc orders;

    @Autowired
    private JdbcTemplate jdbc;

    private final MoveOrderNumber number = new MoveOrderNumber("MO-2026-0042");

    @BeforeEach
    void cleanDatabase() throws IOException {
        jdbc.execute("DROP TABLE IF EXISTS move_order");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/schema.sql")));
    }

    @Test
    void createdOrderCanBeReadAtItsLocation() throws Exception {
        String location = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body("MO-2026-0042", "2-room flat: Abay 10 -> Dostyk 5")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessKey").value("MO-2026-0042"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.title").value("2-room flat: Abay 10 -> Dostyk 5"));
    }

    @Test
    void secondPostOfTheSameKeyIs409AndKeepsOneRow() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("MO-2026-0042", "Office")))
                .andExpect(status().isCreated());

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("MO-2026-0042", "Again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"));

        assertEquals(1, orders.count(number));
        assertEquals("Office", orders.findByNumber(number).orElseThrow().title());
    }

    @Test
    void blankTitleIs400AndStoresNothing() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("MO-2026-0042", " ")))
                .andExpect(status().isBadRequest());

        assertEquals(0, orders.count(number));
    }

    private static String body(String businessKey, String title) {
        return """
                {"businessKey": "%s", "title": "%s"}
                """.formatted(businessKey, title);
    }
}
