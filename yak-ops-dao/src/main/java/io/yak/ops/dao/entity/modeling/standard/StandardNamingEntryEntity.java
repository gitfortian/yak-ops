package io.yak.ops.dao.entity.modeling.standard;

import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persisted Workspace-owned naming-dictionary entry without payload diagnostics. */
@Getter
@Setter
public class StandardNamingEntryEntity extends BaseEntity {
    /** Workspace owning this entry. */
    private String workspaceId;
    /** Workspace-unique root-word abbreviation matched by a table-name rule. */
    private String enCode;
    /** English name of the root word. */
    private String enName;
    /** Chinese name of the root word. */
    private String cnName;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced after every accepted update. */
    private long revision;
}
