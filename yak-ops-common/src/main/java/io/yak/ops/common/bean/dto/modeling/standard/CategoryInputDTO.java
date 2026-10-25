package io.yak.ops.common.bean.dto.modeling.standard;

import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.enums.modeling.standard.StandardNodeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Creates a catalog node under a fixed parent, or a root when parentId is null. */
@Getter
@Setter
public class CategoryInputDTO {
    @Size(max = 64, message = "文本长度超过限制")
    private String parentId;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String name;

    /** Directory or standard set; a missing value creates a directory, matching existing clients. */
    private StandardNodeType nodeType;

    /** Kind a standard set holds; required for a set and forbidden on a directory. */
    private StandardKind standardKind;
}
