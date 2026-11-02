package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Replaces the synonym set of one model field, addressed by model and field code. */
@Getter
@Setter
public class UpdateFieldSynonymsDTO {
    @NotNull(message = "请提供同义词列表")
    private List<String> terms;
}
