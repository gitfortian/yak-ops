package io.yak.ops.common.bean.po.development;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

/** SQL 数据开发任务当前草稿投影。 */
@Data
@TableName("yak_dev_sql_task")
public class SqlTaskPO {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;
  private String name;
  private String description;
  private Long projectId;
  private Long directoryId;
  private Long dataSourceId;
  private String sqlText;
  private String parameterJson;
  private Long draftRevision;
  private Long publishedVersionId;
  private Integer latestVersionNo;
  @TableLogic private Boolean deleted;
  private Instant createTime;
  private Instant updateTime;
}
