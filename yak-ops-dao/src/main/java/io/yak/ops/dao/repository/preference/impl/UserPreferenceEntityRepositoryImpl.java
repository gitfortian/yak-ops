package io.yak.ops.dao.repository.preference.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.dao.entity.preference.UserPreferenceEntity;
import io.yak.ops.dao.mapper.preference.UserPreferenceMapper;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import io.yak.ops.dao.repository.preference.UserPreferenceEntityRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 基于 MyBatis-Plus 实现用户偏好的查询、收藏 Upsert 和使用计数。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Repository
public class UserPreferenceEntityRepositoryImpl extends BaseRepositoryImpl<UserPreferenceMapper, UserPreferenceEntity>
        implements UserPreferenceEntityRepository {

    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Override
    protected UserPreferenceMapper mapper() {
        return userPreferenceMapper;
    }

    @Override
    public Optional<UserPreferenceEntity> queryPreference(String userId, String scene, String itemKey) {
        return Optional.ofNullable(userPreferenceMapper.selectOne(Wrappers.<UserPreferenceEntity>lambdaQuery()
                .eq(UserPreferenceEntity::getUserId, userId)
                .eq(UserPreferenceEntity::getScene, scene)
                .eq(UserPreferenceEntity::getItemKey, itemKey)));
    }

    @Override
    public List<UserPreferenceEntity> queryByUserAndScene(String userId, String scene) {
        return userPreferenceMapper.selectList(Wrappers.<UserPreferenceEntity>lambdaQuery()
                .eq(UserPreferenceEntity::getUserId, userId)
                .eq(UserPreferenceEntity::getScene, scene)
                .orderByDesc(UserPreferenceEntity::getFavorite)
                .orderByAsc(UserPreferenceEntity::getSortOrder)
                .orderByDesc(UserPreferenceEntity::getUseCount)
                .orderByDesc(UserPreferenceEntity::getLastUsedTime)
                .orderByDesc(UserPreferenceEntity::getUpdateTime)
                .orderByDesc(UserPreferenceEntity::getId));
    }

    @Override
    public int upsertFavorite(UserPreferenceEntity entity) {
        return userPreferenceMapper.upsertFavorite(entity);
    }

    @Override
    public int recordUsage(UserPreferenceEntity entity) {
        return userPreferenceMapper.recordUsage(entity);
    }
}
