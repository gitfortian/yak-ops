package io.yak.ops.dao.repository.preference;

import io.yak.ops.dao.entity.preference.UserPreferenceEntity;
import io.yak.ops.dao.repository.BaseRepository;
import java.util.List;
import java.util.Optional;

/**
 * 用户偏好持久化边界。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
public interface UserPreferenceEntityRepository extends BaseRepository<UserPreferenceEntity> {

    Optional<UserPreferenceEntity> queryPreference(String userId, String scene, String itemKey);

    List<UserPreferenceEntity> queryByUserAndScene(String userId, String scene);

    int upsertFavorite(UserPreferenceEntity entity);

    int recordUsage(UserPreferenceEntity entity);
}
