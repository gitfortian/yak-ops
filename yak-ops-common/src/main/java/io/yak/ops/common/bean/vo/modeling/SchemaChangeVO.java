package io.yak.ops.common.bean.vo.modeling;

/**
 * One difference between the current physical table and the logical model.
 *
 * @param kind ADD, CHANGE or DROP
 * @param column affected column name as the model spells it
 * @param fromType observed physical type, null for an added column
 * @param toType physical type the model requires, null for a dropped column
 * @param detail human-readable summary rendered in the publish dialog
 */
public record SchemaChangeVO(String kind, String column, String fromType, String toType, String detail) {}
