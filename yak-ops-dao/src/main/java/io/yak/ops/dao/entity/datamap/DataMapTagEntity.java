package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one Workspace-owned asset tag. */
@Getter
@Setter
public class DataMapTagEntity {
    /** Application-generated tag identity. */
    private String id;
    /** Workspace owning the tag. */
    private String workspaceId;
    /** Workspace-unique tag name. */
    private String name;
    /** Creation timestamp. */
    private LocalDateTime createTime;
    /** Most recent rename timestamp. */
    private LocalDateTime updateTime;
    /** Authenticated creator identity. */
    private String createBy;
    /** Authenticated updater identity. */
    private String updateBy;
}
