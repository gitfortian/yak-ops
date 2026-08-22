package io.yak.ops.business.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetFieldMapper extends BaseMapper<DatasetFieldPO> {
}
