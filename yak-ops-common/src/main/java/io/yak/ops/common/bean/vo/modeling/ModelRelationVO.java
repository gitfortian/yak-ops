package io.yak.ops.common.bean.vo.modeling;

import java.util.List;

/** One semantic relation rendered from the many side, with resolved key pairs and health status. */
public record ModelRelationVO(
        String id,
        String name,
        String fromModelId,
        String fromModelName,
        String fromModelType,
        String toModelId,
        String toModelName,
        String toModelType,
        String joinType,
        String description,
        List<ModelRelationKeyVO> keys,
        String status,
        long revision) {}
