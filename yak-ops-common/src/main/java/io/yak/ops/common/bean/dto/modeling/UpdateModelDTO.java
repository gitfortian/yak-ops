package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Replacement model fields plus the revision the editor observed. */
@Getter
@Setter
public class UpdateModelDTO extends ModelInputDTO {
    @NotNull(message = "缺少数据版本")
    @Min(value = 1, message = "数据版本不合法")
    private Long revision;
}
