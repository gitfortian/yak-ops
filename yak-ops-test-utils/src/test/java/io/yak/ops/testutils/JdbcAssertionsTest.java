package io.yak.ops.testutils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcAssertionsTest {
    @Test
    void comparesNullBinaryAndDuplicateRowsWithoutStringConversion() {
        List<List<?>> expected = List.of(
                Arrays.asList(1, null, new byte[] {1, 2}, new BigDecimal("1.00")),
                Arrays.asList(1, null, new byte[] {1, 2}, new BigDecimal("1.00")));
        JdbcAssertions.assertRows(
                expected,
                List.of(
                        Arrays.asList(1, null, new byte[] {1, 2}, new BigDecimal("1.00")),
                        Arrays.asList(1, null, new byte[] {1, 2}, new BigDecimal("1.00"))));
        assertThrows(AssertionError.class, () -> JdbcAssertions.assertRows(expected, List.of(expected.get(0))));
        assertThrows(
                AssertionError.class,
                () -> JdbcAssertions.assertRows(List.of(Arrays.asList((Object) null)), List.of(List.of(""))));
        assertThrows(
                AssertionError.class,
                () -> JdbcAssertions.assertRows(List.of(List.of(1), List.of(1)), List.of(List.of(1), List.of(2))));
    }

    @Test
    void connectionDiagnosticsDoNotExposeCredentials() {
        var database = new DatabaseMetadata("jdbc:h2:mem:test;PASSWORD=private", "user", "secret", "localhost", 1);
        assertFalse(database.toString().contains("secret"));
        assertFalse(database.toString().contains("private"));
    }
}
