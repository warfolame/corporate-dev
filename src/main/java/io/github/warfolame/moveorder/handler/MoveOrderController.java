package io.github.warfolame.moveorder.handler;

import io.github.warfolame.moveorder.config.MoveOrderService;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP adapter for move orders: one create, one read. It picks the status code;
 * the rules and the storage stay in {@link MoveOrderService}. Failures are turned
 * into {@link ErrorBody} by {@link ApiErrorAdvice}.
 */
@RestController
@RequestMapping("/api/v1/move-orders")
@Tag(name = "Move orders")
public class MoveOrderController {

    private final MoveOrderService service;

    public MoveOrderController(MoveOrderService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a new move order in status REQUESTED")
    @ApiResponse(responseCode = "201", description = "Created. Location is the GET for the new id",
            headers = @Header(name = "Location", description = "URL of the new move order"),
            content = @Content(schema = @Schema(implementation = MoveOrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION: a field is blank, the title is longer than 120, or the body is not JSON",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    @ApiResponse(responseCode = "409", description = "DUPLICATE: an order with this business key already exists; it is kept",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    @ApiResponse(responseCode = "415", description = "UNSUPPORTED: Content-Type is not application/json",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    public ResponseEntity<MoveOrderResponse> create(@Valid @RequestBody CreateMoveOrderRequest request) {
        MoveOrder order = service.register(
                MoveOrderId.newId(), new MoveOrderNumber(request.businessKey()), request.title());

        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}")
                .buildAndExpand(order.id().value())
                .toUri();
        return ResponseEntity.created(location).body(MoveOrderResponse.from(order));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read one move order by its id (the UUID, not the business key)")
    @ApiResponse(responseCode = "200", description = "Found",
            content = @Content(schema = @Schema(implementation = MoveOrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "VALIDATION: id is not a UUID",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    @ApiResponse(responseCode = "404", description = "NOT_FOUND: no move order has this id",
            content = @Content(schema = @Schema(implementation = ErrorBody.class)))
    public MoveOrderResponse get(@PathVariable UUID id) {
        return MoveOrderResponse.from(service.get(new MoveOrderId(id)));
    }
}
