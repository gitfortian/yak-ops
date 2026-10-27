package io.yak.ops.dao.entity.datamap;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one member's favourite asset. */
@Getter
@Setter
public class DataMapFavoriteEntity {
    /** Workspace owning the member and asset. */
    private String workspaceId;
    /** Member who favourited the asset. */
    private String userId;
    /** Favourited asset. */
    private String tableId;
    /** Favourite timestamp. */
    private LocalDateTime createdTime;
}
