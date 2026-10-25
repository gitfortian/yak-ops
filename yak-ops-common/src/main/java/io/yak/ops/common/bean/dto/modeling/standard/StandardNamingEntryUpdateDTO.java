package io.yak.ops.common.bean.dto.modeling.standard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Replacement entry attributes and the revision observed by the editor. */
@Getter
@Setter
public class StandardNamingEntryUpdateDTO extends StandardNamingEntryInputDTO {
    @NotNull(message = "必填参数不能为空")
    @Min(value = 1, message = "版本号必须大于零")
    private Long revision;
}
