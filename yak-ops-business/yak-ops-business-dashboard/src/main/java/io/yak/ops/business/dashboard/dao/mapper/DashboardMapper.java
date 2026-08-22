package io.yak.ops.business.dashboard.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dashboard.dao.model.DashboardPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DashboardMapper extends BaseMapper<DashboardPO> {
}
