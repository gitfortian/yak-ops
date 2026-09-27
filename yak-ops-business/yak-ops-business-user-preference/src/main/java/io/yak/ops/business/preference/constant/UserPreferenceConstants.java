package io.yak.ops.business.preference.constant;

import io.yak.ops.common.constant.CommonConstants;

/**
 * User Preference 与 Boot 共享的稳定协议常量。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public final class UserPreferenceConstants {

    /** User Preference HTTP API 根路径。 */
    public static final String API_PREFIX = CommonConstants.API_PREFIX + "/user-preferences";

    /** 场景内业务标识最大长度。 */
    public static final int ITEM_KEY_MAX_LENGTH = 128;

    private UserPreferenceConstants() {}
}
