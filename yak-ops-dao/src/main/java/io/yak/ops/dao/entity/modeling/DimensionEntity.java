package io.yak.ops.dao.entity.modeling;

import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persists one Workspace concept dimension and its owning scope reference. */
@Getter
@Setter
public class DimensionEntity extends BaseEntity {
    /** Workspace owning the dimension and every permitted scope reference. */
    private String workspaceId;

    /** Owning layer, immutable after creation. */
    private DimensionScope scope;

    /** Owning data domain, set only for public scope. */
    private String domainId;

    /** Owning data mart, set only for application scope. */
    private String datamartId;

    /** Business display name. */
    private String name;

    /** Business English name. */
    private String enName;

    /** Abbreviation unique inside the owning scope. */
    private String code;

    /** Optional business description, null when absent. */
    private String description;

    /** Monotonically increasing version required by update and deletion. */
    private long revision;

    /** Owning data domain name, projected read-only by list and detail queries. */
    private String domainName;

    /** Owning data mart name, projected read-only by list and detail queries. */
    private String datamartName;

    /**
     * Returns the owning scope identity.
     *
     * <p>Mirrors the generated {@code owner_scope_id} column so callers can check uniqueness without
     * duplicating the scope rule.
     *
     * @return the owning data domain or data mart identity, null when neither is set
     */
    public String ownerScope() {
        return domainId != null ? domainId : datamartId;
    }
}
