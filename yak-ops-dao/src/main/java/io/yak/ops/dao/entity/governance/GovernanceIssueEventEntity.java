package io.yak.ops.dao.entity.governance;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Persists one append-only transition of a governance Issue. */
@Getter
@Setter
public class GovernanceIssueEventEntity {
    /** Application-generated event identity. */
    private String id;
    /** Workspace owning the Issue. */
    private String workspaceId;
    /** Issue this transition belongs to. */
    private String issueId;
    /** CREATED, REOPENED, ASSIGNED, IGNORED, UNIGNORED, RESOLVED or AUTO_RESOLVED. */
    private String eventType;
    /** Status before the transition; null for the creation event. */
    private String fromStatus;
    /** Status after the transition. */
    private String toStatus;
    /** Owner carried by an assignment event. */
    private String ownerId;
    /** Window carried by an ignore event. */
    private LocalDateTime ignoreUntil;
    /** Operator note; never an exception message. */
    private String note;
    /** Member who acted, or system for a scan transition. */
    private String actorId;
    /** Event timestamp. */
    private LocalDateTime createTime;
}
