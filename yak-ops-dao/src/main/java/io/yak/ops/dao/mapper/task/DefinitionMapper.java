package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.repository.task.DefinitionPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

/** Task Definition pagination joins only its current immutable version. */
@Mapper
public interface DefinitionMapper extends BaseMapper<DefinitionEntity> {
    @SelectProvider(type = DefinitionSqlProvider.class, method = "selectDefinitionPage")
    IPage<DefinitionEntity> selectDefinitionPage(
            Page<DefinitionEntity> page, @Param("filter") DefinitionPageQuery filter);
}
