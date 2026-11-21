package io.github.warfolame.moveorder.handler;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * The {@code X-Correlation-Id} of one request: the caller's value if it sent one, otherwise a
 * new UUID. Both the advice (400/404/409/500) and the security filters (401/403) use it, so a
 * request gets one id even when it is minted, and both sides can find it in their logs.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";

    private static final String ATTRIBUTE = CorrelationId.class.getName();

    private CorrelationId() {
    }

    public static String of(HttpServletRequest request) {
        Object known = request.getAttribute(ATTRIBUTE);
        if (known instanceof String id) {
            return id;
        }
        String sent = request.getHeader(HEADER);
        String id = sent == null || sent.isBlank() ? UUID.randomUUID().toString() : sent;
        request.setAttribute(ATTRIBUTE, id);
        return id;
    }
}
