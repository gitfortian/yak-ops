package io.yak.ops.common.bean.dto.modeling;

import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable concept dimension fields; the owning scope is part of the editable contract. */
@Getter
@Setter
public class DimensionInputDTO {
    @NotNull(message = "请选择维度归属")
    private DimensionScope scope;

    @Size(max = 64, message = "数据域标识过长")
    private String domainId;

    @Size(max = 64, message = "数据集市标识过长")
    private String datamartId;

    @NotBlank(message = "请输入中文名")
    @Size(max = 128, message = "名称不能超过128个字符")
    private String name;

    @NotBlank(message = "请输入英文名")
    @Size(max = 128, message = "英文名不能超过128个字符")
    private String enName;

    @NotBlank(message = "请输入英文缩写")
    @Size(max = 64, message = "英文缩写不能超过64个字符")
    private String code;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;
}
