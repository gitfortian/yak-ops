package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Replaces the synonym set of one STANDARD_FIELD or METRIC target. Model-field synonyms are
 * addressed by model and field code through their own endpoints, because field row ids are
 * unstable across model saves.
 */
@Getter
@Setter
public class UpdateModelSynonymsDTO {
    @NotBlank(message = "请指定目标类型")
    private String targetType;

    @NotBlank(message = "请指定目标")
    @Size(max = 148, message = "目标标识过长")
    private String targetId;

    @NotNull(message = "请提供同义词列表")
    private List<String> terms;
}
