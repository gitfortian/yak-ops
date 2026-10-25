package io.yak.ops.dao.entity.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.MeasureUnitCategory;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned measurement unit without payload diagnostics. */
@Getter
@Setter
public class StandardMeasureUnitEntity extends BaseEntity {
    /** Workspace owning this unit. */
    private String workspaceId;
    /** Closed category the unit belongs to. */
    private MeasureUnitCategory category;
    /** Workspace-unique abbreviation shown next to a bound field. */
    private String enCode;
    /** English name, such as meter. */
    private String enName;
    /** Chinese name, such as 米. */
    private String cnName;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
