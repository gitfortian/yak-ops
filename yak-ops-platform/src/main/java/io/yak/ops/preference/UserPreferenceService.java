package io.yak.ops.preference;

import io.yak.ops.common.bean.dto.preference.UserPreferenceFavoriteDTO;
import io.yak.ops.common.bean.vo.preference.UserPreferenceVO;
import io.yak.ops.common.enums.preference.UserPreferenceScene;
import java.util.List;

/**
 * 提供当前用户的偏好查询、收藏变更和使用记录能力。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface UserPreferenceService {

    List<UserPreferenceVO> queryPreferences(UserPreferenceScene scene, String userId);

    UserPreferenceVO updateFavorite(
            UserPreferenceScene scene, String itemKey, UserPreferenceFavoriteDTO dto, String userId);

    UserPreferenceVO recordUsage(UserPreferenceScene scene, String itemKey, String userId);
}
