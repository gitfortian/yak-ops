package io.yak.ops.business.governance.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.dao.mapper.governance.GovernanceRetentionMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Pins the chunk loop: cutoff windows, stop-on-partial-chunk and the per-night chunk bound. */
class GovernanceRetentionJobTest {

    private final GovernanceRetentionMapper mapper = mock(GovernanceRetentionMapper.class);

    private final GovernanceRetentionJob job = new GovernanceRetentionJob(mapper);

    @Test
    void stopsWhenAChunkDeletesFewerRowsThanTheChunkSize() {
        AtomicInteger calls = new AtomicInteger();
        when(mapper.deleteScanRunsBefore(any(LocalDateTime.class), anyInt()))
                .thenAnswer(invocation -> calls.incrementAndGet() < 3 ? 500 : 120);

        job.cleanup();

        assertEquals(3, calls.get());
    }

    @Test
    void stopsAtThePerNightChunkBound() {
        when(mapper.deleteScanRunsBefore(any(LocalDateTime.class), anyInt())).thenReturn(500);

        job.cleanup();

        verify(mapper, times(200)).deleteScanRunsBefore(any(LocalDateTime.class), eq(500));
    }

    @Test
    void usesNinetyDayHistoryAndOneHundredEightyDayResolvedCutoffs() {
        LocalDateTime before = LocalDateTime.now();
        job.cleanup();
        LocalDateTime after = LocalDateTime.now();

        verify(mapper).deleteScanRunsBefore(
                windowBetween(before.minusDays(91), after.minusDays(89)), eq(500));
        verify(mapper).deleteHealthSnapshotsBefore(
                windowBetween(before.minusDays(91).toLocalDate(), after.minusDays(89).toLocalDate()), eq(500));
        verify(mapper).deleteResolvedIssuesBefore(
                windowBetween(before.minusDays(181), after.minusDays(179)), eq(500));
    }

    private LocalDateTime windowBetween(LocalDateTime start, LocalDateTime end) {
        return argThat(argument ->
                argument != null && !argument.isBefore(start) && !argument.isAfter(end));
    }

    private LocalDate windowBetween(LocalDate start, LocalDate end) {
        return argThat(argument ->
                argument != null && !argument.isBefore(start) && !argument.isAfter(end));
    }
}
