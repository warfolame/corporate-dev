/**
 * The HTTP edge: {@code /api/v1/move-orders} (create, read, the locked {@code complete}),
 * {@code /api/v1/auth/login} and {@code /api/me}. The controllers, the JSON records (request,
 * response, error), the one advice that picks 400, 401 (login), 403, 404, 409, 415 and 500, and
 * {@link io.github.warfolame.moveorder.handler.SecurityErrors}, which writes the filter chain's 401 and 403.
 */
package io.github.warfolame.moveorder.handler;
