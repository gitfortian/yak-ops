package io.yak.ops.common.bean.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Metadata and optional executable parameters for one Task Definition update. */
@Data
public class UpdateDefinitionDTO {
    @NotBlank
    @Size(max = 128)
    private String name;

    private String parameters;

    @Size(max = 500)
    private String remark;
}
