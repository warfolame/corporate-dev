package io.github.warfolame.moveorder.handler;

import io.github.warfolame.moveorder.domain.DuplicateMoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderNotFound;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The one place that turns exceptions into HTTP status codes and an {@link ErrorBody}.
 * Domain exceptions know nothing about HTTP; only this class knows 400, 404 and 409.
 */
@RestControllerAdvice
public class ApiErrorAdvice {

    static final String CORRELATION_HEADER = "X-Correlation-Id";

    private static final Logger log = LoggerFactory.getLogger(ApiErrorAdvice.class);

    /** {@code @Valid} failed: name each field, then the rule ("title must not be blank"). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalidField(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", message, request);
    }

    /** The path id does not parse as a UUID. 404 stays for a UUID that parses and is missing. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorBody> notAUuid(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", e.getName() + " must be a UUID", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> notJson(HttpMessageNotReadableException e, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION", "body must be JSON", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorBody> wrongContentType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED", "Content-Type must be application/json", request);
    }

    @ExceptionHandler(MoveOrderNotFound.class)
    ResponseEntity<ErrorBody> notFound(MoveOrderNotFound e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage(), request);
    }

    /** The Lab 3 duplicate, now visible to the caller. The first order stays stored. */
    @ExceptionHandler(DuplicateMoveOrder.class)
    ResponseEntity<ErrorBody> duplicate(DuplicateMoveOrder e, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE", e.getMessage(), request);
    }

    /** A path no controller serves, e.g. {@code /api/v1/nothing}. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> noSuchPath(NoResourceFoundException e, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "no such path", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> wrongMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", e.getMethod() + " is not allowed here", request);
    }

    /** A bug nobody mapped. The details go to the log, never into the body. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e, HttpServletRequest request) {
        String correlationId = correlationId(request);
        log.error("unexpected error, correlationId={}", correlationId, e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL", "unexpected error", correlationId);
    }

    private static ResponseEntity<ErrorBody> error(
            HttpStatus status, String code, String message, HttpServletRequest request) {
        return error(status, code, message, correlationId(request));
    }

    private static ResponseEntity<ErrorBody> error(
            HttpStatus status, String code, String message, String correlationId) {
        return ResponseEntity.status(status)
                .header(CORRELATION_HEADER, correlationId)
                .body(new ErrorBody(code, message, correlationId));
    }

    /** The caller's id if it sent one, so both sides can find the same request in their logs. */
    private static String correlationId(HttpServletRequest request) {
        String sent = request.getHeader(CORRELATION_HEADER);
        return sent == null || sent.isBlank() ? UUID.randomUUID().toString() : sent;
    }
}
