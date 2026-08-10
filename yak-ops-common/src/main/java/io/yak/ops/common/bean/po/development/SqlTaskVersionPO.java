package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** SQL 数据开发任务不可变发布版本。 */
@Data
@TableName("yak_dev_sql_task_version")
public class SqlTaskVersionPO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private Long taskId;
  private Integer versionNo;
  private Long dataSourceId;
  private String sqlSnapshot;
  private String parameterSnapshotJson;
  private String contentDigest;
  private Instant publishedAt;
}
