package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Enables or disables one layer's table-name rule under the observed revision. */
@Getter
@Setter
public class NamingRuleUpdateDTO {
    @NotNull(message = "必填参数不能为空")
    private Boolean enabled;

    /**
     * Confirms that the non-conforming models reported by the read endpoint were seen. Only
     * required while enabling; it never exempts those models from later saves.
     */
    private boolean acknowledgeViolations;

    @NotNull(message = "必填参数不能为空")
    @Min(value = 1, message = "版本号必须大于零")
    private Long revision;
}
