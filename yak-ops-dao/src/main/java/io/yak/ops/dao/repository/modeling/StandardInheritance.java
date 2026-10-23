package io.yak.ops.dao.repository.modeling;

import io.yak.ops.common.enums.modeling.standard.DataType;

/**
 * The three attributes a bound field standard forces onto its consumers.
 *
 * @param enCode inherited field code
 * @param dataType inherited logical type
 * @param lengthPrecision inherited length or precision and scale
 */
public record StandardInheritance(String enCode, DataType dataType, String lengthPrecision) {}
