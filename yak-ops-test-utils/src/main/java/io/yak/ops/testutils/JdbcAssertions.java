package io.yak.ops.testutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Typed row assertions preserving NULL, binary data and duplicate multiplicity. */
public final class JdbcAssertions {
    private JdbcAssertions() {}

    public static List<List<Object>> query(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(30);
            try (var result = statement.executeQuery(sql)) {
                List<List<Object>> rows = new ArrayList<>();
                int columns = result.getMetaData().getColumnCount();
                while (result.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int column = 1; column <= columns; column++) row.add(result.getObject(column));
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public static void assertRows(List<? extends List<?>> expected, List<? extends List<?>> actual) {
        assertEquals(expected.size(), actual.size(), "Row count (including duplicates)");
        List<List<?>> remaining = new ArrayList<>(actual);
        for (List<?> row : expected) {
            int match = -1;
            for (int index = 0; index < remaining.size(); index++) {
                if (equalRow(row, remaining.get(index))) {
                    match = index;
                    break;
                }
            }
            assertTrue(match >= 0, "Missing typed row; order is ignored, values and multiplicity are not");
            remaining.remove(match);
        }
    }

    private static boolean equalRow(List<?> left, List<?> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!Objects.deepEquals(left.get(index), right.get(index))) return false;
        }
        return true;
    }
}
