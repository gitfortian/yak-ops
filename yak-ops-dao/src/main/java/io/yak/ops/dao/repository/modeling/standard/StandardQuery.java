package io.yak.ops.dao.repository.modeling.standard;

/** Validated filters and bounded pagination shared by the three new standard kinds. */
public record StandardQuery(String categoryId, String category, String keyword, long offset, int pageSize) {
    @Override
    public String toString() {
        return "StandardQuery[offset=" + offset + ", pageSize=" + pageSize + "]";
    }
}
