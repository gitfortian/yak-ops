package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable layer fields; the preset flag and ordering stay server-owned. */
@Getter
@Setter
public class LayerInputDTO {
    @NotBlank(message = "请输入分层中文名")
    @Size(max = 64, message = "分层中文名不能超过64个字符")
    private String cnName;

    @NotBlank(message = "请输入英文缩写")
    @Size(max = 32, message = "英文缩写不能超过32个字符")
    private String enCode;

    @Size(max = 64, message = "物理数据源标识过长")
    private String dataSourceId;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    @NotNull(message = "请选择分层状态")
    private Boolean enabled;
}
