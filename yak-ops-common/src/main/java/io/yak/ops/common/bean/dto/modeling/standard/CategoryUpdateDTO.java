package io.yak.ops.common.bean.dto.modeling.standard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Renames a directory without changing its parent. */
@Getter
@Setter
public class CategoryUpdateDTO {
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String name;

    @NotNull(message = "必填参数不能为空")
    @Min(value = 1, message = "版本号必须大于零")
    private Long revision;
}
