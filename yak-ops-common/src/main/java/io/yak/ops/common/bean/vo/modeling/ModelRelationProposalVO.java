package io.yak.ops.common.bean.vo.modeling;

/**
 * One proposed join key pair between two models. {@code selectable} is false with a reason when
 * either side lacks a data standard or the types are incompatible; blocked candidates are shown,
 * never silently dropped.
 */
public record ModelRelationProposalVO(
        String fromFieldCode,
        String toFieldCode,
        String fromStandardEnCode,
        String toStandardEnCode,
        boolean sameStandard,
        boolean typesCompatible,
        boolean selectable,
        String reason) {}
