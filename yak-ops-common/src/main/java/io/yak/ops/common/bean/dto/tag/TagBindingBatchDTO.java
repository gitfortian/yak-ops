package io.yak.ops.common.bean.dto.tag;

import io.yak.ops.common.enums.tag.TagBindingOperation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Batch binding command over many assets at once.
 *
 * <p>The one-hundred-table, twenty-value and one-thousand-binding ceilings are product limits
 * reported as {@code BATCH_TOO_LARGE}, so the service enforces them instead of Bean Validation.
 */
@Getter
@Setter
public class TagBindingBatchDTO {

    /** Assets the command applies to. */
    @NotEmpty
    private List<@NotBlank @Size(max = 64) String> tableIds;

    /** Values the command attaches or detaches. */
    @NotEmpty
    private List<@NotBlank @Size(max = 64) String> tagValueIds;

    /** ADD attaches the values, REMOVE detaches them. */
    @NotNull
    private TagBindingOperation operation;
}
