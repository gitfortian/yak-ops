package io.yak.ops.common.bean.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Replacement of one asset's whole tag value set.
 *
 * <p>The twenty-value ceiling is a product limit reported as {@code TOO_MANY_TAGS_PER_ASSET}, so it
 * is enforced by the service rather than by Bean Validation.
 */
@Getter
@Setter
public class TagBindingUpdateDTO {

    /** Value identities that remain attached; an empty list clears the asset. */
    @NotNull
    private List<@NotBlank @Size(max = 64) String> tagValueIds;
}
