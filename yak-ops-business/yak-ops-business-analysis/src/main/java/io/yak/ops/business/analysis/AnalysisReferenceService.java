package io.yak.ops.business.analysis;

import org.springframework.stereotype.Service;

/** Narrow cross-domain port for assets that only need to hold a stable Analysis reference. */
@Service
public class AnalysisReferenceService {

  private final AnalysisService analysisService;

  public AnalysisReferenceService(AnalysisService analysisService) {
    this.analysisService = analysisService;
  }

  public void requireExists(long analysisId) {
    analysisService.get(analysisId);
  }
}
