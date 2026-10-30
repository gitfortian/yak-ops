package io.yak.ops.dao.entity.tag;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one Workspace-owned tag key: a dimension that groups values. */
@Getter
@Setter
public class TagKeyEntity {
    /** Application-generated key identity. */
    private String id;
    /** Workspace owning the key. */
    private String workspaceId;
    /** Workspace-unique dimension name. */
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
