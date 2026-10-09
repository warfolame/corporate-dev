/**
 * Storage of move orders in PostgreSQL over plain JDBC ({@code JdbcTemplate}, {@code ?} placeholders).
 * The table is written by hand in {@code src/main/resources/db/schema.sql}.
 * Translates domain types to columns and back, and a duplicate key to {@code DuplicateMoveOrder}.
 */
package io.github.warfolame.moveorder.persistence;
