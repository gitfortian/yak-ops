package io.yak.ops.common.bean.dto.modeling.standard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Creates a directory under a fixed parent, or a root when parentId is null. */
@Getter
@Setter
public class CategoryInputDTO {
    @Size(max = 64, message = "文本长度超过限制")
    private String parentId;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String name;
}
