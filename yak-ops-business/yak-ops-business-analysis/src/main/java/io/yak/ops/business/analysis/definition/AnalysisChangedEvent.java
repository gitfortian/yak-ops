package io.yak.ops.business.analysis.definition;

/** Committed Analysis mutation fact consumed by derived projections such as lineage. */
public record AnalysisChangedEvent(long analysisId, boolean deleted) {

  public AnalysisChangedEvent {
    if (analysisId <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
  }

  public static AnalysisChangedEvent refreshed(long analysisId) {
    return new AnalysisChangedEvent(analysisId, false);
  }

  public static AnalysisChangedEvent deleted(long analysisId) {
    return new AnalysisChangedEvent(analysisId, true);
  }
}
