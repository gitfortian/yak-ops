package io.yak.ops.common.bean.vo.modeling.standard;

import java.util.List;

/** Workspace directory snapshot with immutable child ownership. */
public record StandardCategoryVO(
        String id, String parentId, String name, long revision, List<StandardCategoryVO> children) {
    public StandardCategoryVO {
        children = List.copyOf(children);
    }

    @Override
    public String toString() {
        return "StandardCategoryVO[id=" + id + ", revision=" + revision + "]";
    }
}
