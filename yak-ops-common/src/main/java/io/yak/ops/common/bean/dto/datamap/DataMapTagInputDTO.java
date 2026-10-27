package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Create or rename request of a Workspace tag. */
@Getter
@Setter
public class DataMapTagInputDTO {

    /** Tag name, unique inside the Workspace. */
    @NotBlank
    @Size(max = 64)
    private String name;
}
