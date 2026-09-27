package io.yak.ops.common.bean.vo.preference;

import io.yak.ops.common.enums.preference.UserPreferenceScene;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 返回当前用户在指定场景下的偏好状态和使用信息。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Data
public class UserPreferenceVO {

    /** 偏好场景。 */
    private UserPreferenceScene scene;

    /** 场景内稳定业务标识，例如 product id 或 datasource type。 */
    private String itemKey;

    /** 是否被用户显式收藏。 */
    private Boolean favorite;

    /** 收藏展示顺序，未收藏时为 0。 */
    private Integer sortOrder;

    /** 累计使用次数。 */
    private Long useCount;

    /** 最近一次使用时间。 */
    private LocalDateTime lastUsedTime;
}
