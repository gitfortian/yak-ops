package io.yak.ops.dao.entity.modeling;

import io.yak.ops.common.enums.modeling.StoragePolicy;
import io.yak.ops.dao.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** Persists one DWD or DIM logical model plus the joined names used for display. */
@Getter
@Setter
public class ModelEntity extends BaseEntity {
    /** Workspace owning the model and every reference it carries. */
    private String workspaceId;
    /**
     * Persisted model kind as plain text: a hand-written row may carry a value this build does not
     * know, and mapping it to an enum would fail the read instead of surfacing the row.
     */
    private String type;
    /** Selected warehouse layer supplying the table name prefix. */
    private String layerId;
    /** Owning planning category. */
    private String categoryId;
    /** Owning business process; set only for DWD. */
    private String processId;
    /** Public-layer owner; set only when the model is anchored to a data domain. */
    private String domainId;
    /** Application-layer owner; set only when the model is anchored to a data mart. */
    private String datamartId;
    /** Concept dimension a DIM table describes. */
    private String dimensionId;
    /** Full table name including the layer prefix, unique inside the Workspace. */
    private String name;
    /** Business display name. */
    private String cnName;
    /** Declared storage strategy. */
    private StoragePolicy storagePolicy;
    /** Data retention in days. */
    private int lifecycleDays;
    /** Assigned owner identity; retained after membership ends. */
    private String ownerId;
    /** Optional business description. */
    private String description;
    /** Optimistic version advanced by every accepted update. */
    private long revision;
    /** Joined layer display name; null on write paths. */
    private String layerName;
    /** Joined category name; null on write paths. */
    private String categoryName;
    /** Joined business process name; null on write paths or for DIM. */
    private String processName;
    /** Joined data domain name; null on write paths or for application-layer models. */
    private String domainName;
    /** Joined data mart name; null on write paths or for public-layer models. */
    private String datamartName;
    /** Joined concept dimension name; null on write paths or when omitted. */
    private String dimensionName;
    /** Counted fields; zero on write paths. */
    private long fieldCount;
}
