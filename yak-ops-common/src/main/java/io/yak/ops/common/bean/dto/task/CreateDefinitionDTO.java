package io.yak.ops.common.bean.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Generic Task Definition creation request. */
@Data
public class CreateDefinitionDTO {
    @NotBlank @Size(max = 128)
    private String name;
    @NotBlank @Size(max = 64)
    private String taskType;
    @NotBlank
    private String parameters;
    @Size(max = 500)
    private String remark;
}
