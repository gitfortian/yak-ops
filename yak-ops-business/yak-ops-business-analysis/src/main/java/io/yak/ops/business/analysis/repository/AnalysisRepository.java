package io.yak.ops.business.analysis.repository;

import io.yak.ops.business.analysis.AnalysisAsset;
import io.yak.ops.business.analysis.AnalysisDraft;
import java.util.List;
import java.util.Optional;

/** Domain repository. Persistence rows and JSON strings must not cross this boundary. */
public interface AnalysisRepository {

  long insert(AnalysisDraft draft);

  void update(long analysisId, AnalysisDraft draft);

  Optional<AnalysisAsset> findById(long analysisId);

  List<AnalysisAsset> list();

  void delete(long analysisId);
}
