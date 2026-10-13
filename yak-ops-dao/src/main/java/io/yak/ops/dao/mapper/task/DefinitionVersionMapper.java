package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Maps immutable Task Definition version snapshots through MyBatis.
 */
@Mapper
public interface DefinitionVersionMapper extends BaseMapper<DefinitionVersionEntity> {}
