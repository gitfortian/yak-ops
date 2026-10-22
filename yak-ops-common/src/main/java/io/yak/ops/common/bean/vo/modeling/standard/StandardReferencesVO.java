package io.yak.ops.common.bean.vo.modeling.standard;
/** Null reference values distinguish unavailable capability from a completed reference query. */
public record StandardReferencesVO(String availability, Boolean referenced, Long count) {
    public static StandardReferencesVO notEnabled() {
        return new StandardReferencesVO("NOT_ENABLED", null, null);
    }
}
