package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Replacement of an asset's tag set. */
@Getter
@Setter
public class DataMapTagAssignmentDTO {

    /** Tag identities that remain attached; an empty list detaches every tag. */
    @NotNull
    @Size(max = 50)
    private List<@NotBlank @Size(max = 64) String> tagIds;
}
