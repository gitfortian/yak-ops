package io.yak.ops.business.datamap.support;

import io.yak.ops.common.enums.datamap.DataMapErrorCode;
import io.yak.ops.common.exception.BusinessException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Caps one member at five catalog searches per second.
 *
 * <p>The counter is an in-process bucket, sound because the product is single-node. A shared
 * counter would require the clustered deployment that does not exist, and the ceiling sits above
 * what a 500 ms debounce can produce, so it fires on a held Enter rather than on normal typing.
 */
@Component
public class DataMapSearchRateLimiter {

    private static final int PERMITS = 5;
    private static final long WINDOW_MILLIS = 1_000L;

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    /**
     * Records one search or rejects it when the caller exceeded the window.
     *
     * @param workspaceId current Workspace
     * @param userId authenticated caller
     */
    public void acquire(String workspaceId, String userId) {
        long now = System.nanoTime() / 1_000_000L;
        Deque<Long> window = windows.computeIfAbsent(workspaceId + ':' + userId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) window.pollFirst();
            if (window.size() >= PERMITS) throw new BusinessException(DataMapErrorCode.SEARCH_TOO_FREQUENT);
            window.addLast(now);
        }
    }
}
