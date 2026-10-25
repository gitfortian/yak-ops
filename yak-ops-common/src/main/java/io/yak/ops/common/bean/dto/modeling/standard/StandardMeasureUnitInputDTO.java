package io.yak.ops.common.bean.dto.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.MeasureUnitCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable attributes of one measurement unit. */
@Getter
@Setter
public class StandardMeasureUnitInputDTO {
    @NotNull(message = "必填参数不能为空")
    private MeasureUnitCategory category;

    /** Abbreviation, such as {@code m}; unique inside the Workspace. */
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 64, message = "文本长度超过限制")
    private String enCode;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String enName;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String cnName;

    @Size(max = 500, message = "文本长度超过限制")
    private String description;
}
