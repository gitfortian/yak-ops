package io.yak.ops.dao.entity.tag;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one term of a key: a value that can be bound to an asset. */
@Getter
@Setter
public class TagValueEntity {
    /** Application-generated value identity. */
    private String id;
    /** Workspace owning the value. */
    private String workspaceId;
    /** Key the value belongs to. */
    private String keyId;
    /** Key-unique term name. */
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
