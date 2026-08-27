package io.yak.ops.business.digitalscreen.dao.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.sql.Timestamp;
import lombok.Data;

@Data
@TableName("yak_digital_screen")
public class DigitalScreenPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String name;
  private String description;
  private String templateId;
  private Integer templateVersion;
  private String status;
  private String bindingsJson;
  private Timestamp publishedTime;
  private Timestamp createTime;
  private Timestamp updateTime;
}
