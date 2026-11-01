package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Per-user read marker for one asset's comment thread. */
@Getter
@Setter
public class DataMapCommentReadEntity {

    /** Application-generated identity. */
    private String id;

    /** Workspace owning this marker. */
    private String workspaceId;

    /** Anchored asset id. */
    private String targetId;

    /** Reader user id. */
    private String userId;

    /** Newest comment the reader has seen. */
    private LocalDateTime lastReadTime;
}
