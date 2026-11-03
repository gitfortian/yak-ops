package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Declares the meaning of one row of the model. */
@Getter
@Setter
public class UpdateModelGrainDTO {
    @Size(max = 200, message = "粒度声明不能超过200个字符")
    private String grainNote;

    @NotNull(message = "缺少数据版本")
    @Min(value = 1, message = "数据版本不合法")
    private Long revision;
}
