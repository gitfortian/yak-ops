package io.yak.ops.common.bean.vo.modeling;

import io.yak.ops.common.enums.modeling.NodeKind;
import java.util.List;
import lombok.Getter;

/** Authorized planning snapshot; contains no Workspace or account-private fields. */
@Getter
public class PlanningNodeVO {
    private final String id;
    private final NodeKind kind;
    private final String parentId;
    private final String name;
    private final String code;
    private final String ownerId;
    private final String ownerName;
    private final boolean ownerActive;
    private final String description;
    private final long revision;
    private final List<PlanningNodeVO> children;

    public PlanningNodeVO(
            String id,
            NodeKind kind,
            String parentId,
            String name,
            String code,
            String ownerId,
            String ownerName,
            boolean ownerActive,
            String description,
            long revision,
            List<PlanningNodeVO> children) {
        this.id = id;
        this.kind = kind;
        this.parentId = parentId;
        this.name = name;
        this.code = code;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.ownerActive = ownerActive;
        this.description = description;
        this.revision = revision;
        this.children = List.copyOf(children);
    }
}
