package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Requests key-pair proposals between the current model and one target DIM model. */
@Getter
@Setter
public class RelationProposalQueryDTO {
    @NotBlank(message = "请选择目标维度表")
    @Size(max = 64, message = "目标模型标识过长")
    private String targetModelId;
}
