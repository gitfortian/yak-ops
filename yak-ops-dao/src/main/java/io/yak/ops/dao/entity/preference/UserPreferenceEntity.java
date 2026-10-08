package io.yak.ops.dao.entity.preference;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 映射 yak_ops_user_preference 表，保存用户级收藏状态和使用频次。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@Setter
@ToString
@TableName("yak_ops_user_preference")
public class UserPreferenceEntity extends BaseEntity {

    /** 偏好所属用户 ID。 */
    private String userId;

    /** 偏好场景，例如 PRODUCT_MENU。 */
    private String scene;

    /** 场景内稳定业务标识。 */
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
