package io.yak.ops.common.bean.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Rename request of a tag key. */
@Getter
@Setter
public class TagKeyRenameDTO {

    /** New dimension name, unique inside the Workspace. */
    @NotBlank
    @Size(max = 64)
    private String name;
}
