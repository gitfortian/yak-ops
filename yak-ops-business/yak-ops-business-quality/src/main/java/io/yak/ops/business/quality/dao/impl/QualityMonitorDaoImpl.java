package io.yak.ops.business.quality.dao.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.dao.QualityMonitorDao;
import io.yak.ops.business.quality.dao.mapper.QualityAlertEventMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorMapper;
import io.yak.ops.business.quality.dao.mapper.QualityMonitorSettingMapper;
import io.yak.ops.business.quality.dao.mapper.QualityQueryMapper;
import io.yak.ops.business.quality.dao.mapper.QualityRuleMapper;
import io.yak.ops.business.quality.dao.mapper.QualityTableAssetMapper;
import io.yak.ops.business.quality.dao.mapper.QualityWriteMapper;
import io.yak.ops.common.bean.po.quality.QualityAlertEventPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.MonitorRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableAssetRow;
import io.yak.ops.common.bean.po.quality.QualityQueryPO.TableMonitorSummaryRow;
import io.yak.ops.common.bean.po.quality.QualityRulePO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnQualityEnabled
@DependsOn("qualityFlyway")
public class QualityMonitorDaoImpl implements QualityMonitorDao {
  private final QualityQueryMapper queryMapper;
  private final QualityWriteMapper writeMapper;
  private final QualityMonitorMapper monitorMapper;
  private final QualityRuleMapper ruleMapper;
  private final QualityMonitorSettingMapper settingMapper;
  private final QualityTableAssetMapper tableAssetMapper;
  private final QualityAlertEventMapper alertEventMapper;

  @Override public long countMonitors(Map<String, Object> params) { return queryMapper.countMonitors(params); }
  @Override public List<MonitorRow> selectMonitors(Map<String, Object> params) { return queryMapper.selectMonitors(params); }
  @Override public MonitorRow selectMonitor(long id) { return queryMapper.selectMonitor(id); }
  @Override public List<TableMonitorSummaryRow> selectTableSummaries(Map<String, Object> params) { return queryMapper.selectTableSummaries(params); }

  @Override
  public long insertMonitor(QualityMonitorPO monitor) {
    monitorMapper.insert(monitor);
    if (monitor.getId() == null) throw new IllegalStateException("质量监控创建成功，但未返回监控编号");
    return monitor.getId();
  }

  @Override
  public boolean updateMonitor(QualityMonitorPO monitor) {
    return monitorMapper.update(
        monitor,
        Wrappers.<QualityMonitorPO>lambdaUpdate()
            .eq(QualityMonitorPO::getId, monitor.getId())
            .eq(QualityMonitorPO::getDeleted, false)) > 0;
  }

  @Override
  public boolean softDeleteMonitor(long id) {
    int affected = monitorMapper.update(
        null,
        Wrappers.<QualityMonitorPO>lambdaUpdate()
            .eq(QualityMonitorPO::getId, id)
            .eq(QualityMonitorPO::getDeleted, false)
            .set(QualityMonitorPO::getDeleted, true)
            .set(QualityMonitorPO::getEnabled, false));
    if (affected > 0) {
      ruleMapper.update(
          null,
          Wrappers.<QualityRulePO>lambdaUpdate()
              .eq(QualityRulePO::getMonitorId, id)
              .eq(QualityRulePO::getDeleted, false)
              .set(QualityRulePO::getDeleted, true)
              .set(QualityRulePO::getEnabled, false));
    }
    return affected > 0;
  }

  @Override
  public boolean existsMonitorTarget(
      Long excludeId, long dataSourceId, String databaseName, String schemaName, String tableName) {
    var query = Wrappers.<QualityMonitorPO>lambdaQuery()
        .eq(QualityMonitorPO::getDeleted, false)
        .eq(QualityMonitorPO::getDataSourceId, dataSourceId)
        .eq(QualityMonitorPO::getTableName, tableName);
    if (databaseName == null) query.isNull(QualityMonitorPO::getDatabaseName);
    else query.eq(QualityMonitorPO::getDatabaseName, databaseName);
    if (schemaName == null) query.isNull(QualityMonitorPO::getSchemaName);
    else query.eq(QualityMonitorPO::getSchemaName, schemaName);
    if (excludeId != null) query.ne(QualityMonitorPO::getId, excludeId);
    return monitorMapper.selectCount(query) > 0;
  }

  @Override public void lockMonitor(long monitorId) { writeMapper.lockMonitor(monitorId); }

  @Override
  public boolean updateMonitorResult(long monitorId, String executionNo, String result, LocalDateTime runTime) {
    return monitorMapper.update(
        null,
        Wrappers.<QualityMonitorPO>lambdaUpdate()
            .eq(QualityMonitorPO::getId, monitorId)
            .eq(QualityMonitorPO::getDeleted, false)
            .set(QualityMonitorPO::getLastExecutionNo, executionNo)
            .set(QualityMonitorPO::getLastResult, result)
            .set(QualityMonitorPO::getLastRunTime, runTime)) > 0;
  }

  @Override
  public List<QualityRulePO> selectRules(long monitorId) {
    return ruleMapper.selectList(
        Wrappers.<QualityRulePO>lambdaQuery()
            .eq(QualityRulePO::getMonitorId, monitorId)
            .eq(QualityRulePO::getDeleted, false)
            .orderByAsc(QualityRulePO::getSortOrder)
            .orderByAsc(QualityRulePO::getId));
  }

  @Override
  public void replaceRules(long monitorId, List<QualityRulePO> rules) {
    ruleMapper.update(
        null,
        Wrappers.<QualityRulePO>lambdaUpdate()
            .eq(QualityRulePO::getMonitorId, monitorId)
            .eq(QualityRulePO::getDeleted, false)
            .set(QualityRulePO::getDeleted, true)
            .set(QualityRulePO::getEnabled, false));
    for (QualityRulePO rule : rules) ruleMapper.insert(rule);
  }

  @Override public QualityMonitorSettingPO selectSetting(long monitorId) { return settingMapper.selectById(monitorId); }
  @Override public void upsertSetting(QualityMonitorSettingPO setting) { writeMapper.upsertMonitorSetting(setting); }
  @Override public List<QualityMonitorSettingPO> selectDue(LocalDateTime now, int limit) { return queryMapper.selectDueMonitors(now, Math.max(1, limit)); }
  @Override public boolean claimSchedule(long monitorId, LocalDateTime expectedRunTime, LocalDateTime nextRunTime) { return writeMapper.claimMonitorSchedule(monitorId, expectedRunTime, nextRunTime) == 1; }
  @Override public void insertAlert(QualityAlertEventPO alert) { alertEventMapper.insert(alert); }

  @Override public long countTableAssets(Map<String, Object> params) { return queryMapper.countTableAssets(params); }
  @Override public List<TableAssetRow> selectTableAssets(Map<String, Object> params) { return queryMapper.selectTableAssets(params); }

  @Override
  public List<QualityTableAssetPO> selectTableAssetTargets(long dataSourceId, String databaseName) {
    var query = Wrappers.<QualityTableAssetPO>lambdaQuery()
        .eq(QualityTableAssetPO::getDeleted, false)
        .eq(QualityTableAssetPO::getDataSourceId, dataSourceId);
    if (databaseName != null) query.eq(QualityTableAssetPO::getDatabaseName, databaseName);
    return tableAssetMapper.selectList(query);
  }

  @Override
  public boolean existsTableAssetTarget(long dataSourceId, String databaseName, String schemaName, String tableName) {
    return tableAssetMapper.selectCount(
        Wrappers.<QualityTableAssetPO>lambdaQuery()
            .eq(QualityTableAssetPO::getDeleted, false)
            .eq(QualityTableAssetPO::getDataSourceId, dataSourceId)
            .eq(QualityTableAssetPO::getDatabaseName, databaseName == null ? "" : databaseName)
            .eq(QualityTableAssetPO::getSchemaName, schemaName == null ? "" : schemaName)
            .eq(QualityTableAssetPO::getTableName, tableName)) > 0;
  }

  @Override
  public int upsertTableAssets(List<QualityTableAssetPO> assets) {
    for (QualityTableAssetPO asset : assets) writeMapper.upsertTableAsset(asset);
    return assets.size();
  }

  @Override public int countMonitorsForAsset(long assetId) { return queryMapper.countMonitorsForAsset(assetId); }

  @Override
  public boolean softDeleteTableAsset(long assetId) {
    return tableAssetMapper.update(
        null,
        Wrappers.<QualityTableAssetPO>lambdaUpdate()
            .eq(QualityTableAssetPO::getId, assetId)
            .eq(QualityTableAssetPO::getDeleted, false)
            .set(QualityTableAssetPO::getDeleted, true)
            .set(QualityTableAssetPO::getUpdatedAt, LocalDateTime.now())) > 0;
  }
}
