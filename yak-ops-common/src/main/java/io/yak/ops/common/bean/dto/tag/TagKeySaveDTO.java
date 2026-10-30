package io.yak.ops.common.bean.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Creation request of a tag key, optionally seeding its first values.
 *
 * <p>The two-hundred-value ceiling of one key is a product limit reported as
 * {@code TOO_MANY_TAG_VALUES_PER_KEY}, so the service enforces it instead of Bean Validation.
 */
@Getter
@Setter
public class TagKeySaveDTO {

    /** Dimension name, unique inside the Workspace. */
    @NotBlank
    @Size(max = 64)
    private String name;

    /** Terms created with the key in the same transaction; may be empty. */
    private List<@NotBlank @Size(max = 128) String> values;
}
