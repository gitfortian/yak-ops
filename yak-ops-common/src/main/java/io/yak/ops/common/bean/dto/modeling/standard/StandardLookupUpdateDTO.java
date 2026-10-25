package io.yak.ops.common.bean.dto.modeling.standard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Replaces attributes and the whole value list under the observed revision. */
@Getter
@Setter
public class StandardLookupUpdateDTO extends StandardLookupInputDTO {
    @NotNull(message = "必填参数不能为空")
    @Min(value = 1, message = "版本号必须大于零")
    private Long revision;
}
