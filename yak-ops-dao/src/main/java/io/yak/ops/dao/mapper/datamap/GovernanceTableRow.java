package io.yak.ops.dao.mapper.datamap;

import lombok.Getter;
import lombok.Setter;

/** Raw catalog row the governance data-map rules read. Never carries credentials or data values. */
@Getter
@Setter
public class GovernanceTableRow {
    /** Asset identity. */
    private String id;
    /** Schema-qualified display name. */
    private String displayName;
    /** Curated business description. */
    private String description;
    /** Engine-reported table comment. */
    private String remarks;
    /** Curated accountable member. */
    private String ownerId;
}
