package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Replaces editable subject area fields only when the retained revision still matches. */
@Getter
@Setter
public class UpdateSubjectDTO extends SubjectInputDTO {
    @NotNull(message = "缺少数据版本")
    @Positive(message = "数据版本无效")
    private Long revision;
}
