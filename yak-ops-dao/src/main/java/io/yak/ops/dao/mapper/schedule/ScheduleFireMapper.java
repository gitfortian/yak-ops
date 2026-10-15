package io.yak.ops.dao.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.schedule.ScheduleFireEntity;
import org.apache.ibatis.annotations.Mapper;

/** Maps final occurrence rows under the Workspace-scoped repository boundary. */
@Mapper
public interface ScheduleFireMapper extends BaseMapper<ScheduleFireEntity> {}
