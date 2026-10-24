package io.yak.ops.common.bean.vo.modeling;

/**
 * One model hanging below a planning process.
 *
 * @param id model identity
 * @param name business display name
 * @param code full table name including the layer prefix
 */
public record LogicalModelSummaryVO(String id, String name, String code) {}
