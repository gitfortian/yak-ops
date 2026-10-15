package io.yak.ops.dao.repository.schedule.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.enums.schedule.ScheduleFireOutcome;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.entity.schedule.ScheduleFireEntity;
import io.yak.ops.dao.mapper.schedule.ScheduleFireMapper;
import io.yak.ops.dao.mapper.schedule.ScheduleMapper;
import io.yak.ops.dao.repository.schedule.ScheduleFireRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Persists final outcomes and keeps retention from reopening previously resolved occurrences. */
@Repository
public class ScheduleFireRepositoryImpl implements ScheduleFireRepository {
    @Resource
    private ScheduleFireMapper mapper;

    @Resource
    private ScheduleMapper schedules;

    @Override
    public Optional<ScheduleFireEntity> find(String workspaceId, String requestId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ScheduleFireEntity>lambdaQuery()
                .eq(ScheduleFireEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleFireEntity::getRequestId, requestId)));
    }

    @Override
    public Optional<ScheduleFireEntity> findForUpdate(String workspaceId, String requestId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ScheduleFireEntity>lambdaQuery()
                .eq(ScheduleFireEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleFireEntity::getRequestId, requestId)
                .last("FOR UPDATE")));
    }

    @Override
    public void add(ScheduleFireEntity fire) {
        mapper.insert(fire);
    }

    @Override
    public PageData<ScheduleFireEntity> page(
            String workspaceId,
            String scheduleId,
            int pageNo,
            int pageSize,
            ScheduleFireOutcome outcome,
            LocalDateTime from,
            LocalDateTime until) {
        var page = mapper.selectPage(
                new Page<>(pageNo, pageSize),
                Wrappers.<ScheduleFireEntity>lambdaQuery()
                        .eq(ScheduleFireEntity::getWorkspaceId, workspaceId)
                        .eq(ScheduleFireEntity::getScheduleId, scheduleId)
                        .eq(outcome != null, ScheduleFireEntity::getOutcome, outcome)
                        .ge(from != null, ScheduleFireEntity::getScheduledTime, from)
                        .lt(until != null, ScheduleFireEntity::getScheduledTime, until)
                        .orderByDesc(ScheduleFireEntity::getScheduledTime, ScheduleFireEntity::getId));
        return new PageData<>(page.getRecords(), page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retire(String workspaceId, String scheduleId, LocalDateTime before) {
        ScheduleEntity schedule = schedules.selectOne(Wrappers.<ScheduleEntity>lambdaQuery()
                .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleEntity::getId, scheduleId)
                .last("FOR UPDATE"));
        if (schedule == null || (schedule.getRetiredBefore() != null && !before.isAfter(schedule.getRetiredBefore()))) {
            return;
        }
        schedules.update(
                null,
                Wrappers.<ScheduleEntity>lambdaUpdate()
                        .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                        .eq(ScheduleEntity::getId, scheduleId)
                        .set(ScheduleEntity::getRetiredBefore, before));
        mapper.delete(Wrappers.<ScheduleFireEntity>lambdaQuery()
                .eq(ScheduleFireEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleFireEntity::getScheduleId, scheduleId)
                .lt(ScheduleFireEntity::getScheduledTime, before));
    }
}
