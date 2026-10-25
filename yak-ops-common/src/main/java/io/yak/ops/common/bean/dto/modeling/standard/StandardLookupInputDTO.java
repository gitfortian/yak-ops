package io.yak.ops.common.bean.dto.modeling.standard;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Creates a lookup table together with its ordered value list. */
@Getter
@Setter
public class StandardLookupInputDTO {
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 64, message = "文本长度超过限制")
    private String enCode;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String cnName;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String enName;

    @Size(max = 500, message = "文本长度超过限制")
    private String description;

    /**
     * The complete ordered value list; null is rejected as a missing list. Elements are validated
     * by the service so an empty or duplicated value code reports {@code INVALID_LOOKUP_VALUE}
     * instead of a generic parameter failure.
     */
    private List<StandardLookupValueInputDTO> values;
}
