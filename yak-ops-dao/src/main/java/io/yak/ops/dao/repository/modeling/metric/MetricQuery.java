package io.yak.ops.dao.repository.modeling.metric;

/** Validated filters and bounded pagination shared by the five metric kinds. */
public record MetricQuery(String processId, String domainId, String keyword, long offset, int pageSize) {
    @Override
    public String toString() {
        return "MetricQuery[offset=" + offset + ", pageSize=" + pageSize + "]";
    }
}
