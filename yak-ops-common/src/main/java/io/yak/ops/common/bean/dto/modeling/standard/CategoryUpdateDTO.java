package io.yak.ops.common.bean.dto.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.StandardKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Renames a node without changing its parent, and adjusts a standard set's kind while it is empty. */
@Getter
@Setter
public class CategoryUpdateDTO {
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String name;

    @NotNull(message = "必填参数不能为空")
    @Min(value = 1, message = "版本号必须大于零")
    private Long revision;

    /**
     * Replacement kind of a standard set; null leaves the current kind untouched. A directory
     * rejects this field, and a set that already holds a record rejects a change.
     */
    private StandardKind standardKind;
}
