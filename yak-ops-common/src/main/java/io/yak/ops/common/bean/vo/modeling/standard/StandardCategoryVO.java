package io.yak.ops.common.bean.vo.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.enums.modeling.standard.StandardNodeType;
import java.util.List;

/** Workspace catalog snapshot with immutable child ownership and the node's container shape. */
public record StandardCategoryVO(
        String id,
        String parentId,
        String name,
        StandardNodeType nodeType,
        StandardKind standardKind,
        long recordCount,
        long revision,
        List<StandardCategoryVO> children) {
    public StandardCategoryVO {
        children = List.copyOf(children);
    }

    @Override
    public String toString() {
        return "StandardCategoryVO[id=" + id + ", revision=" + revision + "]";
    }
}
