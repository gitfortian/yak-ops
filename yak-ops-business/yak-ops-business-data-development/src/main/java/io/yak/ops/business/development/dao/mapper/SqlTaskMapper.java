package io.yak.ops.business.development.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.development.SqlTaskPO;
import org.apache.ibatis.annotations.Param;

public interface SqlTaskMapper extends BaseMapper<SqlTaskPO> {
  SqlTaskPO selectForUpdate(@Param("id") Long id);
}
