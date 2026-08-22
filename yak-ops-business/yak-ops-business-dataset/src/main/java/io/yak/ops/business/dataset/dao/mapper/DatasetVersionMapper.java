package io.yak.ops.business.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataset.dao.model.DatasetVersionPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetVersionMapper extends BaseMapper<DatasetVersionPO> {
}
