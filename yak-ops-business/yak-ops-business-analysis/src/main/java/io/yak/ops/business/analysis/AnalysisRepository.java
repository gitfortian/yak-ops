package io.yak.ops.business.analysis;

import java.util.List;
import java.util.Optional;

interface AnalysisRepository {

  long insert(
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      String querySpecJson,
      String visualConfigJson);

  void update(
      long analysisId,
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      String querySpecJson,
      String visualConfigJson);

  Optional<AnalysisRow> findById(long analysisId);

  List<AnalysisRow> list();

  void delete(long analysisId);

  record AnalysisRow(
      long id,
      String name,
      String description,
      long datasetId,
      AnalysisChartType chartType,
      String querySpecJson,
      String visualConfigJson,
      java.time.Instant createTime,
      java.time.Instant updateTime) {
  }
}
