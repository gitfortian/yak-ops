package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one asset-to-tag association. */
@Getter
@Setter
public class DataMapTableTagEntity {
    /** Workspace owning the asset and tag. */
    private String workspaceId;
    /** Tagged asset. */
    private String tableId;
    /** Applied tag. */
    private String tagId;
    /** Association timestamp. */
    private LocalDateTime createTime;
    /** Authenticated tagger identity. */
    private String createBy;
}
