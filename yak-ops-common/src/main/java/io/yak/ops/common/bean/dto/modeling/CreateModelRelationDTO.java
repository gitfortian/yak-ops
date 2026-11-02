package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Creates one many-to-one semantic relation from the current model to a DIM model. */
@Getter
@Setter
public class CreateModelRelationDTO {
    @NotBlank(message = "请选择目标维度表")
    @Size(max = 64, message = "目标模型标识过长")
    private String targetModelId;

    /** Optional; auto-generated from both model names when absent. */
    @Size(max = 64, message = "关系名不能超过64个字符")
    private String name;

    /** INNER or LEFT; LEFT when absent. */
    @Size(max = 16, message = "Join 类型不合法")
    private String joinType;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    @Valid
    @NotNull(message = "请提供关联键")
    private List<RelationKeyPairDTO> keys;
}
