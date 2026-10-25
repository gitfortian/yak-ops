package io.yak.ops.common.bean.vo.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.StandardReferenceKind;
import java.util.List;

/**
 * Reference list of one standard record.
 *
 * <p>{@code referenced} and {@code count} are derived from {@code items}, so the three can never
 * disagree. {@code null} distinguishes a capability that is unavailable from a completed query that
 * found nothing.
 */
public record StandardReferencesVO(String availability, Boolean referenced, Long count, List<Item> items) {
    public StandardReferencesVO {
        items = items == null ? null : List.copyOf(items);
    }

    /**
     * One dependant an operator can act on.
     *
     * @param kind what the item is
     * @param id identity needed to open it; a model field reports its owning model
     * @param name display name of the item
     * @param path locator an operator can search for, such as {@code dwd_trade_order.order_amt}
     */
    public record Item(StandardReferenceKind kind, String id, String name, String path) {}

    public static StandardReferencesVO notEnabled() {
        return new StandardReferencesVO("NOT_ENABLED", null, null, null);
    }

    public static StandardReferencesVO of(List<Item> items) {
        var copies = items == null ? List.<Item>of() : List.copyOf(items);
        return new StandardReferencesVO("AVAILABLE", !copies.isEmpty(), (long) copies.size(), copies);
    }
}
