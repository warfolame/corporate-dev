package io.github.warfolame.moveorder.handler;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JSON body of every failure. Only these three fields: no stack trace,
 * no exception class name.
 *
 * @param code          stable token a client can switch on: VALIDATION, NOT_FOUND, DUPLICATE, ...
 * @param message       one sentence for a person
 * @param correlationId the caller's {@code X-Correlation-Id}, or a new one if it sent none
 */
public record ErrorBody(
        @Schema(example = "VALIDATION") String code,
        @Schema(example = "title must not be blank") String message,
        @Schema(example = "3f6c1d1e-7f44-4a51-9d2b-0b8c1f5d2a90") String correlationId) {
}
