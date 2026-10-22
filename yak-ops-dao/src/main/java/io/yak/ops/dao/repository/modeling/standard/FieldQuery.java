package io.yak.ops.dao.repository.modeling.standard;
/** Validated filters and bounded pagination; keyword is an escaped bound LIKE value. */
public record FieldQuery(String categoryId, String keyword, long offset, int pageSize) {
    @Override
    public String toString() {
        return "FieldQuery[offset=" + offset + ", pageSize=" + pageSize + "]";
    }
}
