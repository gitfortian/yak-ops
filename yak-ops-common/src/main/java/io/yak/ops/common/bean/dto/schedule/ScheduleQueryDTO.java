package io.yak.ops.common.bean.dto.schedule;

import io.yak.ops.common.bean.dto.common.PageQueryDTO;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Bounded Workspace schedule filters with a stable server-owned sort order. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ScheduleQueryDTO extends PageQueryDTO {
    /** Optional exact root target type. */
    private ScheduleTargetType targetType;
    /** Optional exact target identifier. */
    @Size(max = 64)
    private String targetId;
    /** Optional bounded target selection; mutually exclusive with targetId. */
    @Size(min = 1, max = 200)
    private List<@NotBlank @Size(max = 64) String> targetIds;
    /** Optional desired enablement filter, not registration health. */
    private Boolean enabled;
}
