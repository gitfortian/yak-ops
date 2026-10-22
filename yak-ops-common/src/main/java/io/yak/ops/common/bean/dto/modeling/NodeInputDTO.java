package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable planning fields; trusted identity and parentage are excluded. */
@Getter
@Setter
public class NodeInputDTO {
    @NotBlank(message = "请输入中文名")
    @Size(max = 128, message = "名称不能超过128个字符")
    private String name;

    @NotBlank(message = "请输入英文编码")
    @Size(max = 64, message = "英文编码不能超过64个字符")
    private String code;

    @NotBlank(message = "请选择负责人")
    @Size(max = 64, message = "负责人标识无效")
    private String ownerId;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;
}
