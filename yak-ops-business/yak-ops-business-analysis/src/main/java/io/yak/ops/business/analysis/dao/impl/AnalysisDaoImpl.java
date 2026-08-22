package io.yak.ops.business.analysis.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.analysis.dao.AnalysisDao;
import io.yak.ops.business.analysis.dao.mapper.AnalysisMapper;
import io.yak.ops.business.analysis.dao.model.AnalysisPO;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@DependsOn("yakAnalysisFlyway")
@ConditionalOnDataSourceEnabled
public class AnalysisDaoImpl implements AnalysisDao {

  private final AnalysisMapper mapper;

  public AnalysisDaoImpl(AnalysisMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public long insert(AnalysisPO value) {
    Timestamp now = Timestamp.from(Instant.now());
    value.setCreateTime(now);
    value.setUpdateTime(now);
    if (mapper.insert(value) != 1 || value.getId() == null) {
      throw new IllegalStateException("创建 Analysis 后未返回主键");
    }
    return value.getId();
  }

  @Override
  public void update(long analysisId, AnalysisPO value) {
    int updated = mapper.update(null, Wrappers.<AnalysisPO>lambdaUpdate()
        .set(AnalysisPO::getName, value.getName())
        .set(AnalysisPO::getDescription, value.getDescription())
        .set(AnalysisPO::getDatasetId, value.getDatasetId())
        .set(AnalysisPO::getChartType, value.getChartType())
        .set(AnalysisPO::getQuerySpecJson, value.getQuerySpecJson())
        .set(AnalysisPO::getVisualConfigJson, value.getVisualConfigJson())
        .set(AnalysisPO::getUpdateTime, Timestamp.from(Instant.now()))
        .eq(AnalysisPO::getId, analysisId));
    if (updated != 1) throw new IllegalArgumentException("Analysis 不存在：" + analysisId);
  }

  @Override
  public Optional<AnalysisPO> findById(long analysisId) {
    return Optional.ofNullable(mapper.selectById(analysisId));
  }

  @Override
  public List<AnalysisPO> list() {
    return mapper.selectList(Wrappers.<AnalysisPO>lambdaQuery()
        .orderByDesc(AnalysisPO::getUpdateTime)
        .orderByDesc(AnalysisPO::getId));
  }

  @Override
  public void delete(long analysisId) {
    if (mapper.deleteById(analysisId) != 1) {
      throw new IllegalArgumentException("Analysis 不存在：" + analysisId);
    }
  }
}
