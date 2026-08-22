package io.yak.ops.business.analysis.service.event;

public record AnalysisLineageRefreshRequested(long analysisId, boolean deleted) {

  public AnalysisLineageRefreshRequested {
    if (analysisId <= 0L) throw new IllegalArgumentException("analysisId 必须大于 0");
  }

  public static AnalysisLineageRefreshRequested refresh(long analysisId) {
    return new AnalysisLineageRefreshRequested(analysisId, false);
  }

  public static AnalysisLineageRefreshRequested deleted(long analysisId) {
    return new AnalysisLineageRefreshRequested(analysisId, true);
  }
}
