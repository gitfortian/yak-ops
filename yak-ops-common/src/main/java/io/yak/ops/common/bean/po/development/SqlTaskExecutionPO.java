package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** SQL task execution ledger used by manual and workflow runs. */
@Data
@TableName("yak_dev_sql_task_execution")
public class SqlTaskExecutionPO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private Long taskId;
  private Long taskVersionId;
  private Integer taskVersionNo;
  private Long dataSourceId;
  private String sqlSnapshot;
  private String inputJson;
  private String idempotencyKey;
  private String status;
  private Long affectedRows;
  private String outputJson;
  private String errorMessage;
  private Instant createTime;
  private Instant startTime;
  private Instant finishTime;
}
