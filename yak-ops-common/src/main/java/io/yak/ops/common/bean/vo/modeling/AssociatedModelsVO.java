package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.page.PagingData;

/**
 * Models attached to one business process.
 *
 * <p>{@code availability} stays a string so a client built before logical modeling shipped can keep
 * rendering its "not enabled" hint; this build always answers {@code AVAILABLE}.
 *
 * @param availability capability state, {@code AVAILABLE} once modeling exists
 * @param page one page of attached models, null only for a capability that is not available
 */
public record AssociatedModelsVO(String availability, PagingData<LogicalModelSummaryVO> page) {
    public static final String AVAILABLE = "AVAILABLE";

    public static AssociatedModelsVO available(PagingData<LogicalModelSummaryVO> page) {
        return new AssociatedModelsVO(AVAILABLE, page);
    }
}
