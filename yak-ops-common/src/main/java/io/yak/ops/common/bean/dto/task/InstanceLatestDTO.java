package io.yak.ops.common.bean.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** Bounded Workspace-scoped Instance summary request for the visible Task Definition page. */
@Data
public class InstanceLatestDTO {
    /** Definition identities selected by the caller's current page. */
    @NotEmpty
    @Size(max = 200)
    private List<@NotBlank String> definitionIds;

    /** Select the newest active Instance instead of the newest Instance of any status. */
    private boolean activeOnly;
}
