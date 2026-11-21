package io.github.warfolame.moveorder.handler;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import io.github.warfolame.moveorder.domain.DuplicateMoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNotFound;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Web slice: only the controller, the advice and the real filter chain ({@link SecurityConfig})
 * are started. The service is a mock, so no PostgreSQL and no real HTTP server are involved.
 * {@code Application} lives in {@code config}, not above this package, so the test names it.
 *
 * <p>Since Lab 5 every call needs a token: {@link #perform} sends a dispatcher's ({@code jwt()}).
 * The Lab 4 statuses below are the same as before, now with that token.
 */
@WebMvcTest(MoveOrderController.class)
@ContextConfiguration(classes = Application.class)
@Import(SecurityConfig.class)
class MoveOrderControllerTest {

    private static final String PATH = "/api/v1/move-orders";

    private static final RequestPostProcessor DISPATCHER_TOKEN = jwt()
            .jwt(token -> token.subject("aliya"))
            .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MoveOrderService service;

    /** The chain needs a decoder bean; jwt() never calls it. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Every call in this class carries a dispatcher's token. */
    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(DISPATCHER_TOKEN));
    }

    @Test
    void withoutATokenCreateIs401Not201() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body("MO-2026-0042", "2-room flat: Abay 10 -> Dostyk 5")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(service);
    }

    private final MoveOrderNumber number = new MoveOrderNumber("MO-2026-0042");

    @Test
    void validBodyIs201WithLocationOfTheNewId() throws Exception {
        when(service.register(any(), any(), any())).thenAnswer(call -> MoveOrder.requested(
                call.getArgument(0), call.getArgument(1), call.getArgument(2)));

        MvcResult result = perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(body("MO-2026-0042", "2-room flat: Abay 10 -> Dostyk 5")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.businessKey").value("MO-2026-0042"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.title").value("2-room flat: Abay 10 -> Dostyk 5"))
                .andReturn();

        ArgumentCaptor<MoveOrderId> id = ArgumentCaptor.forClass(MoveOrderId.class);
        verify(service).register(id.capture(), eq(number), eq("2-room flat: Abay 10 -> Dostyk 5"));
        String newId = id.getValue().value().toString();
        header().string("Location", endsWith(PATH + "/" + newId)).match(result);
        jsonPath("$.id").value(newId).match(result);
    }

    /** The five bodies from the lab: four are 400 before the service runs, one is a row. */
    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("MO-2026-0042", "", "title must not be blank"),
                Arguments.of("MO-2026-0042", "   ", "title must not be blank"),
                Arguments.of("", "Office", "businessKey must not be blank"),
                Arguments.of("MO-2026-0042", "x".repeat(121), "title size must be between 0 and 120"));
    }

    @ParameterizedTest(name = "businessKey=\"{0}\", title of {1} -> 400 \"{2}\"")
    @MethodSource("invalidBodies")
    void invalidBodyIs400AndTheServiceDoesNotRun(String businessKey, String title, String message) throws Exception {
        perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(businessKey, title)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value(message));

        verifyNoInteractions(service);
    }

    @Test
    void titleOfExactly120Is201() throws Exception {
        when(service.register(any(), any(), any())).thenAnswer(call -> MoveOrder.requested(
                call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        String title = "x".repeat(120);

        perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("MO-2026-0042", title)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.title").value(title));
    }

    @Test
    void duplicateBusinessKeyIs409() throws Exception {
        when(service.register(any(), any(), any())).thenThrow(new DuplicateMoveOrder(number, null));

        perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("MO-2026-0042", "Again")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.message").value("duplicate move order: MO-2026-0042"));
    }

    @Test
    void existingIdIs200() throws Exception {
        MoveOrder order = MoveOrder.requested(MoveOrderId.newId(), number, "Office");
        when(service.get(order.id())).thenReturn(order);

        perform(get(PATH + "/" + order.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.id().value().toString()))
                .andExpect(jsonPath("$.businessKey").value("MO-2026-0042"));
    }

    @Test
    void missingIdIs404() throws Exception {
        MoveOrderId missing = MoveOrderId.newId();
        when(service.get(missing)).thenThrow(new MoveOrderNotFound(missing));

        perform(get(PATH + "/" + missing.value()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void businessKeyInThePathIs400NotA404() throws Exception {
        perform(get(PATH + "/MO-2026-0042"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("id must be a UUID"));

        verifyNoInteractions(service);
    }

    @Test
    void bodyThatIsNotJsonIs400() throws Exception {
        perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.message").value("body must be JSON"));
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        perform(post(PATH).contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED"));
    }

    @Test
    void callersCorrelationIdComesBackOnA400() throws Exception {
        perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "abc-123")
                        .content(body("MO-2026-0042", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void missingCorrelationIdGetsANewOne() throws Exception {
        perform(get(PATH + "/MO-2026-0042"))
                .andExpect(jsonPath("$.correlationId").value(not("")));
    }

    @Test
    void unexpectedErrorIs500WithoutInternals() throws Exception {
        when(service.get(any())).thenThrow(new IllegalStateException("connection pool exhausted"));

        perform(get(PATH + "/" + UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value("unexpected error"))
                .andExpect(jsonPath("$.*", hasSize(3)))
                .andExpect(jsonPath("$.message", not(containsString("pool"))));
    }

    private static String body(String businessKey, String title) {
        return """
                {"businessKey": "%s", "title": "%s"}
                """.formatted(businessKey, title);
    }
}
