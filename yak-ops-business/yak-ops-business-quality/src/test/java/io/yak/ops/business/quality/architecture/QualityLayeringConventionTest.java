package io.yak.ops.business.quality.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.quality.dao.QualityAnalyticsDao;
import io.yak.ops.business.quality.dao.QualityCatalogDao;
import io.yak.ops.business.quality.dao.QualityExecutionDao;
import io.yak.ops.business.quality.dao.QualityMonitorDao;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import io.yak.ops.business.quality.repository.QualityExecutionWorkspaceRepository;
import io.yak.ops.business.quality.repository.QualityRepository;
import io.yak.ops.business.quality.repository.QualityWorkspaceRepository;
import io.yak.ops.common.bean.po.quality.QualityAlertEventPO;
import io.yak.ops.common.bean.po.quality.QualityExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorPO;
import io.yak.ops.common.bean.po.quality.QualityMonitorSettingPO;
import io.yak.ops.common.bean.po.quality.QualityRuleExecutionPO;
import io.yak.ops.common.bean.po.quality.QualityRulePO;
import io.yak.ops.common.bean.po.quality.QualityRuleTemplatePO;
import io.yak.ops.common.bean.po.quality.QualityTableAssetPO;
import io.yak.ops.common.bean.po.quality.QualityTemplateFolderPO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityLayeringConventionTest {

  @Test
  void repositoryBoundariesShouldNotExposeHttpOrPersistenceContracts() {
    assertCleanBoundaries(List.of(
        QualityRepository.class,
        CustomTemplateRepository.class,
        QualityWorkspaceRepository.class,
        QualityExecutionWorkspaceRepository.class));
  }

  @Test
  void daoBoundariesShouldNotExposeHttpContracts() {
    for (Class<?> type : List.of(
        QualityCatalogDao.class,
        QualityMonitorDao.class,
        QualityExecutionDao.class,
        QualityAnalyticsDao.class)) {
      for (Method method : type.getMethods()) {
        assertThat(signature(method))
            .as("DAO transport boundary: %s#%s", type.getSimpleName(), method.getName())
            .doesNotContain(".bean.dto.quality")
            .doesNotContain(".bean.vo.quality");
      }
    }
  }

  @Test
  void tablePosShouldStayOneToOneWithExistingQualityTables() {
    assertTable(QualityRuleTemplatePO.class, "yak_quality_rule_template");
    assertTable(QualityMonitorPO.class, "yak_quality_monitor");
    assertTable(QualityRulePO.class, "yak_quality_rule");
    assertTable(QualityExecutionPO.class, "yak_quality_execution");
    assertTable(QualityRuleExecutionPO.class, "yak_quality_rule_execution");
    assertTable(QualityTableAssetPO.class, "yak_quality_table_asset");
    assertTable(QualityMonitorSettingPO.class, "yak_quality_monitor_setting");
    assertTable(QualityAlertEventPO.class, "yak_quality_alert_event");
    assertTable(QualityTemplateFolderPO.class, "yak_quality_template_folder");
  }

  private void assertCleanBoundaries(List<Class<?>> types) {
    for (Class<?> type : types) {
      for (Method method : type.getMethods()) {
        assertThat(signature(method))
            .as("Repository boundary: %s#%s", type.getSimpleName(), method.getName())
            .doesNotContain(".bean.dto.quality")
            .doesNotContain(".bean.vo.quality")
            .doesNotContain(".bean.po.quality");
      }
    }
  }

  private String signature(Method method) {
    StringBuilder signature = new StringBuilder(method.getGenericReturnType().getTypeName());
    for (var parameter : method.getGenericParameterTypes()) {
      signature.append('|').append(parameter.getTypeName());
    }
    return signature.toString();
  }

  private void assertTable(Class<?> type, String expected) {
    TableName tableName = type.getAnnotation(TableName.class);
    assertThat(tableName).as(type.getSimpleName() + " @TableName").isNotNull();
    assertThat(tableName.value()).isEqualTo(expected);
  }
}
