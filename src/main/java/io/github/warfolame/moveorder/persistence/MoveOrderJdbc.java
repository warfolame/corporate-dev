package io.github.warfolame.moveorder.persistence;

import io.github.warfolame.moveorder.domain.DuplicateMoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrder;
import io.github.warfolame.moveorder.domain.MoveOrderId;
import io.github.warfolame.moveorder.domain.MoveOrderNumber;
import io.github.warfolame.moveorder.domain.MoveOrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The {@code move_order} table from {@code db/schema.sql}, over plain JDBC.
 * Callers get domain types or a domain exception; they never see SQL, a
 * {@code Connection} or a {@code ResultSet}. Transactions are the caller's job.
 */
@Repository
public class MoveOrderJdbc {

    private static final String INSERT = """
            INSERT INTO move_order (id, business_key, status, title, deposit_paid)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String COUNT = """
            SELECT count(*) FROM move_order WHERE business_key = ?
            """;

    private static final String FIND = """
            SELECT id, business_key, status, title, deposit_paid
            FROM move_order
            WHERE business_key = ?
            """;

    private final JdbcTemplate jdbc;

    public MoveOrderJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @throws DuplicateMoveOrder if an order with the same number is already stored
     */
    public void insert(MoveOrder order) {
        try {
            jdbc.update(INSERT,
                    order.id().value(),
                    order.number().value(),
                    toColumn(order.status()),
                    order.title(),
                    order.depositPaid());
        } catch (DuplicateKeyException e) {
            // the DataIntegrityViolationException for SQLState 23505 (unique violation).
            // Rethrown unchecked, so the caller's transaction rolls back.
            throw new DuplicateMoveOrder(order.number(), e);
        }
    }

    /** How many stored orders have this number: 0 or 1, because business_key is UNIQUE. */
    public int count(MoveOrderNumber number) {
        Integer n = jdbc.queryForObject(COUNT, Integer.class, number.value());
        return n == null ? 0 : n;
    }

    public Optional<MoveOrder> find(MoveOrderNumber number) {
        return jdbc.query(FIND, MoveOrderJdbc::toOrder, number.value()).stream().findFirst();
    }

    private static MoveOrder toOrder(ResultSet rs, int rowNum) throws SQLException {
        return new MoveOrder(
                new MoveOrderId(rs.getObject("id", UUID.class)),
                new MoveOrderNumber(rs.getString("business_key")),
                rs.getString("title"),
                toStatus(rs.getString("status")),
                rs.getBoolean("deposit_paid"));
    }

    /** Domain status -> the text the CHECK constraint allows. */
    static String toColumn(MoveOrderStatus status) {
        return switch (status) {
            case REQUESTED -> "REQUESTED";
            case SCHEDULED -> "SCHEDULED";
            case IN_PROGRESS -> "IN_PROGRESS";
            case COMPLETED -> "COMPLETED";
        };
    }

    /** Text from the table -> domain status. Unknown text is a bug, not a new status. */
    static MoveOrderStatus toStatus(String column) {
        return switch (column) {
            case "REQUESTED" -> MoveOrderStatus.REQUESTED;
            case "SCHEDULED" -> MoveOrderStatus.SCHEDULED;
            case "IN_PROGRESS" -> MoveOrderStatus.IN_PROGRESS;
            case "COMPLETED" -> MoveOrderStatus.COMPLETED;
            case null, default -> throw new IllegalStateException("unknown move order status in database: " + column);
        };
    }
}
