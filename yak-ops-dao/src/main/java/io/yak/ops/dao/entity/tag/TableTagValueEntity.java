package io.yak.ops.dao.entity.tag;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one binding of a tag value to a catalog asset. */
@Getter
@Setter
public class TableTagValueEntity {
    /** Workspace owning the asset and the value. */
    private String workspaceId;
    /** Bound catalog asset. */
    private String tableId;
    /** Bound tag value. */
    private String tagValueId;
    /** Binding timestamp. */
    private LocalDateTime createTime;
    /** Authenticated binder identity. */
    private String createBy;
}
