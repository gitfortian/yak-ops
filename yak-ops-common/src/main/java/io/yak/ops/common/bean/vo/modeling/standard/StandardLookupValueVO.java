package io.yak.ops.common.bean.vo.modeling.standard;

/** One ordered value of a lookup table. */
public record StandardLookupValueVO(int ordinal, String valueCode, String cnName, String enName, String meaning) {}
