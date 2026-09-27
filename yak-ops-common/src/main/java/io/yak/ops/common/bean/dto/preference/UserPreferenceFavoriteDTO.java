package io.yak.ops.common.bean.dto.preference;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户收藏状态变更输入契约。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class UserPreferenceFavoriteDTO {

    /** 是否收藏当前偏好项。 */
    @NotNull(message = "收藏状态不能为空")
    private Boolean favorite;
}
