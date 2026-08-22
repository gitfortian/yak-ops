package io.yak.ops.business.sync.realtime.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.sync.realtime.dao.model.RealtimeDefinitionVersionPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RealtimeDefinitionVersionMapper extends BaseMapper<RealtimeDefinitionVersionPO> {}
