package io.yak.ops.common.bean.dto.modeling.standard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable attributes of one naming-dictionary entry. */
@Getter
@Setter
public class StandardNamingEntryInputDTO {
    /** Root-word abbreviation matched by a table-name rule; unique inside the Workspace. */
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
