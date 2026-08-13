package io.yak.ops.plugin.database.jdbc;

import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormFieldVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.FormSectionVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.JdbcUrlLinkageVO;
import io.yak.ops.common.bean.vo.datasource.DataSourcePluginConfigVO.VisibilityConditionVO;
import java.util.ArrayList;
import java.util.List;

/** JDBC URL Schema 标准能力：由插件声明模板，前端统一完成 Host / Port / Database 双向联动。 */
public final class JdbcUrlSchemaSupport {

  private static final String JDBC_URL_FIELD = "jdbcUrl";
  private static final String SSH_FIELD = "sshTunnel";
  private static final String SSH_ENABLED_FIELD = "sshTunnel.enabled";

  private JdbcUrlSchemaSupport() {
  }

  public static DataSourcePluginConfigVO apply(
      DataSourcePluginConfigVO config,
      String template) {
    if (config == null || template == null || template.trim().isEmpty()) {
      return config;
    }

    if (config.getSections() != null) {
      for (FormSectionVO section : config.getSections()) {
        if (section == null || section.getFields() == null) continue;
        section.getFields().forEach(field -> configureField(field, template));
      }
    }
    if (config.getFormFields() != null) {
      config.getFormFields().forEach(field -> configureField(field, template));
    }
    return config;
  }

  private static void configureField(FormFieldVO field, String template) {
    if (field == null || !JDBC_URL_FIELD.equals(field.getKey())) return;

    field.setType("JDBC_URL");
    field.setPlaceholder("根据主机、端口和数据库自动生成，也可以直接修改");
    field.setUrlLinkage(
        JdbcUrlLinkageVO.builder()
            .template(template.trim())
            .hostField("host")
            .portField("port")
            .databaseField("database")
            .preserveSuffix(true)
            .build());

    List<String> dependencies =
        field.getDependsOn() == null
            ? new ArrayList<>()
            : new ArrayList<>(field.getDependsOn());
    // SSH 是一个复合对象字段；监听父字段可确保开关变化一定触发前端重新计算可见性。
    if (!dependencies.contains(SSH_FIELD)) {
      dependencies.add(SSH_FIELD);
    }
    field.setDependsOn(dependencies);

    List<VisibilityConditionVO> conditions =
        field.getVisibleWhen() == null
            ? new ArrayList<>()
            : new ArrayList<>(field.getVisibleWhen());
    boolean hasSshVisibilityRule =
        conditions.stream()
            .anyMatch(
                condition ->
                    condition != null
                        && SSH_ENABLED_FIELD.equals(condition.getField())
                        && "FALSY".equalsIgnoreCase(condition.getOperator()));
    if (!hasSshVisibilityRule) {
      conditions.add(
          VisibilityConditionVO.builder()
              .field(SSH_ENABLED_FIELD)
              .operator("FALSY")
              .build());
    }
    field.setVisibleWhen(conditions);
  }
}
