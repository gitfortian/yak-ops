package io.yak.ops.common.bean.vo.modeling;

/** Explicit unavailable state while logical modeling is not enabled. */
public record AssociatedModelsVO(String availability, Void page) {
    public static AssociatedModelsVO notEnabled() {
        return new AssociatedModelsVO("NOT_ENABLED", null);
    }
}
