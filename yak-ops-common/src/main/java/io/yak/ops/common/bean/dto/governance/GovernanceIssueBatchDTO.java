package io.yak.ops.common.bean.dto.governance;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Batch command envelope shared by every Issue transition. */
@Getter
@Setter
public class GovernanceIssueBatchDTO {

    /** Issues the command applies to; the whole batch is rejected above two hundred. */
    @NotEmpty
    @Size(max = 200)
    private List<@Size(max = 64) String> issueIds;

    /** Assignee of an assign command; ignored by the other commands. */
    @Size(max = 64)
    private String ownerId;

    /** Ignore deadline of an ignore command; must be in the future and within ninety days. */
    private java.time.LocalDateTime ignoreUntil;

    /** Operator note of five hundred characters at most. */
    @Size(max = 500)
    private String note;
}
