package io.yak.ops.dao.mapper.datasource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Maps datasource persistence operations to MyBatis statements.
 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSourceEntity> {}
