package io.yak.ops.business.datasource.domain;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/** 数据源业务定义；与 MyBatis PO 和 HTTP VO 解耦。 */
@Data
public class DataSourceDefinition {
  private Long id;
  private String name;
  private DataSourceDbType dbType;
  private String jdbcUrl;
  private DataSourceEnvironment environment;
  private DataSourceConnStatus connStatus;
  private String remark;
  @ToString.Exclude private String connectionParams;
  @ToString.Exclude private String originalJson;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
