package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Replaces the editable surface of one semantic relation under an optimistic revision. */
@Getter
@Setter
public class UpdateModelRelationDTO {
    @NotBlank(message = "请输入关系名")
    @Size(max = 64, message = "关系名不能超过64个字符")
    private String name;

    @NotBlank(message = "请选择 Join 类型")
    @Size(max = 16, message = "Join 类型不合法")
    private String joinType;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    @Valid
    @NotNull(message = "请提供关联键")
    private List<RelationKeyPairDTO> keys;

    @NotNull(message = "缺少数据版本")
    @Min(value = 1, message = "数据版本不合法")
    private Long revision;
}
