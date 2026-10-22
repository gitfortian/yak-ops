package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.NodeKind;

/** Identity and revision committed by a planning mutation. */
public record SavedNodeVO(String id, NodeKind kind, long revision) {}
