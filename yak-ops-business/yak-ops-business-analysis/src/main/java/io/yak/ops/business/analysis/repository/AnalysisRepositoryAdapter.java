package io.yak.ops.business.analysis.repository;

import io.yak.ops.business.analysis.AnalysisAsset;
import io.yak.ops.business.analysis.AnalysisChartType;
import io.yak.ops.business.analysis.AnalysisDraft;
import io.yak.ops.business.analysis.dao.AnalysisDao;
import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import io.yak.ops.business.analysis.repository.support.AnalysisJsonCodec;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakAnalysisFlyway")
@ConditionalOnDataSourceEnabled
public class AnalysisRepositoryAdapter implements AnalysisRepository {

  private final AnalysisDao dao;
  private final AnalysisJsonCodec jsonCodec;

  public AnalysisRepositoryAdapter(AnalysisDao dao, AnalysisJsonCodec jsonCodec) {
    this.dao = dao;
    this.jsonCodec = jsonCodec;
  }

  @Override
  public long insert(AnalysisDraft draft) {
    return dao.insert(toPO(draft));
  }

  @Override
  public void update(long analysisId, AnalysisDraft draft) {
    dao.update(analysisId, toPO(draft));
  }

  @Override
  public Optional<AnalysisAsset> findById(long analysisId) {
    return dao.findById(analysisId).map(this::toDomain);
  }

  @Override
  public List<AnalysisAsset> list() {
    return dao.list().stream().map(this::toDomain).toList();
  }

  @Override
  public void delete(long analysisId) {
    dao.delete(analysisId);
  }

  private AnalysisPO toPO(AnalysisDraft draft) {
    AnalysisPO value = new AnalysisPO();
    value.setName(draft.name());
    value.setDescription(draft.description());
    value.setDatasetId(draft.datasetId());
    value.setChartType(draft.chartType().name());
    value.setQuerySpecJson(jsonCodec.writeQuerySpec(draft.querySpec()));
    value.setVisualConfigJson(jsonCodec.writeVisualConfig(draft.visualConfig()));
    return value;
  }

  private AnalysisAsset toDomain(AnalysisPO value) {
    long id = value.getId();
    return new AnalysisAsset(
        id,
        value.getName(),
        value.getDescription(),
        value.getDatasetId(),
        AnalysisChartType.valueOf(value.getChartType()),
        jsonCodec.readQuerySpec(value.getQuerySpecJson(), id),
        jsonCodec.readVisualConfig(value.getVisualConfigJson(), id),
        value.getCreateTime() == null ? null : value.getCreateTime().toInstant(),
        value.getUpdateTime() == null ? null : value.getUpdateTime().toInstant());
  }
}
