package io.yak.ops.common.bean.vo.governance;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** One append-only transition of an Issue. */
@Getter
@Setter
public class GovernanceIssueEventVO {

    private String id;

    private String eventType;

    private String fromStatus;

    private String toStatus;

    private String ownerId;

    private LocalDateTime ignoreUntil;

    private String note;

    private String actorId;

    private LocalDateTime createTime;
}
