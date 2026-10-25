package io.yak.ops.common.bean.vo.modeling.metric;

import io.yak.ops.common.enums.modeling.metric.MetricReferenceKind;
import java.util.List;

/**
 * Reference list of one metric record.
 *
 * <p>{@code referenced} and {@code count} are derived from {@code items}, so the three can never
 * disagree. Composite metrics expose no reference list yet because nothing can reference them.
 */
public record MetricReferencesVO(String availability, Boolean referenced, Long count, List<Item> items) {
    public MetricReferencesVO {
        items = items == null ? null : List.copyOf(items);
    }

    /**
     * One dependant an operator can act on.
     *
     * @param kind what the item is
     * @param id identity needed to open it; a model field reports its owning model
     * @param name display name of the item
     * @param path locator an operator can search for, such as {@code dws_trade_order.pay_amt}
     */
    public record Item(MetricReferenceKind kind, String id, String name, String path) {}

    public static MetricReferencesVO of(List<Item> items) {
        var copies = items == null ? List.<Item>of() : List.copyOf(items);
        return new MetricReferencesVO("AVAILABLE", !copies.isEmpty(), (long) copies.size(), copies);
    }
}
