package io.yak.ops.preference.impl;

import io.yak.ops.preference.UserPreferenceService;
import io.yak.ops.preference.constant.UserPreferenceConstants;
import io.yak.ops.common.bean.dto.preference.UserPreferenceFavoriteDTO;
import io.yak.ops.common.bean.vo.preference.UserPreferenceVO;
import io.yak.ops.common.enums.preference.UserPreferenceScene;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.DateUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.preference.UserPreferenceEntity;
import io.yak.ops.dao.repository.preference.UserPreferenceEntityRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 实现用户级收藏和使用信号的持久化，并保证偏好只属于当前认证用户。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Service
public class UserPreferenceServiceImpl implements UserPreferenceService {

    @Resource
    private UserPreferenceEntityRepository userPreferenceRepository;

    @Override
    public List<UserPreferenceVO> queryPreferences(UserPreferenceScene scene, String userId) {
        String ownerUserId = requireUserId(userId);
        UserPreferenceScene preferenceScene = requireScene(scene);
        return userPreferenceRepository.queryByUserAndScene(ownerUserId, preferenceScene.name()).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreferenceVO updateFavorite(
            UserPreferenceScene scene, String itemKey, UserPreferenceFavoriteDTO dto, String userId) {
        String ownerUserId = requireUserId(userId);
        UserPreferenceScene preferenceScene = requireFavoriteScene(scene);
        String preferenceItemKey = requireItemKey(itemKey);
        if (dto == null || dto.getFavorite() == null) throw new IllegalArgumentException("favorite 不能为空");

        UserPreferenceEntity existing = userPreferenceRepository
                .queryPreference(ownerUserId, preferenceScene.name(), preferenceItemKey)
                .orElse(null);
        boolean favorite = dto.getFavorite();
        if (existing != null && Boolean.valueOf(favorite).equals(existing.getFavorite())) return toVO(existing);

        UserPreferenceEntity preference =
                existing == null ? newPreference(ownerUserId, preferenceScene, preferenceItemKey) : existing;
        preference.setFavorite(favorite);
        preference.setSortOrder(favorite ? nextFavoriteSortOrder(ownerUserId, preferenceScene, existing) : 0);
        if (existing == null) preference.initCreate(ownerUserId);
        else preference.initUpdate(ownerUserId);

        if (userPreferenceRepository.upsertFavorite(preference) <= 0) {
            throw new IllegalStateException("用户偏好收藏状态保存失败");
        }
        return requirePreference(ownerUserId, preferenceScene, preferenceItemKey);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserPreferenceVO recordUsage(UserPreferenceScene scene, String itemKey, String userId) {
        String ownerUserId = requireUserId(userId);
        UserPreferenceScene preferenceScene = requireUsageScene(scene);
        String preferenceItemKey = requireItemKey(itemKey);
        LocalDateTime usedAt = DateUtils.now();

        UserPreferenceEntity preference = newPreference(ownerUserId, preferenceScene, preferenceItemKey);
        preference.setUseCount(1L);
        preference.setLastUsedTime(usedAt);
        preference.initCreate(ownerUserId);

        if (userPreferenceRepository.recordUsage(preference) <= 0) {
            throw new IllegalStateException("用户偏好使用记录保存失败");
        }
        return requirePreference(ownerUserId, preferenceScene, preferenceItemKey);
    }

    private UserPreferenceEntity newPreference(String userId, UserPreferenceScene scene, String itemKey) {
        UserPreferenceEntity preference = new UserPreferenceEntity();
        preference.setUserId(userId);
        preference.setScene(scene.name());
        preference.setItemKey(itemKey);
        preference.setFavorite(false);
        preference.setSortOrder(0);
        preference.setUseCount(0L);
        return preference;
    }

    private int nextFavoriteSortOrder(String userId, UserPreferenceScene scene, UserPreferenceEntity existing) {
        if (existing != null
                && Boolean.TRUE.equals(existing.getFavorite())
                && existing.getSortOrder() != null
                && existing.getSortOrder() > 0) {
            return existing.getSortOrder();
        }
        return userPreferenceRepository.queryByUserAndScene(userId, scene.name()).stream()
                        .filter(item -> Boolean.TRUE.equals(item.getFavorite()))
                        .map(UserPreferenceEntity::getSortOrder)
                        .filter(order -> order != null && order > 0)
                        .max(Integer::compareTo)
                        .orElse(0)
                + 1;
    }

    private UserPreferenceVO requirePreference(String userId, UserPreferenceScene scene, String itemKey) {
        return userPreferenceRepository
                .queryPreference(userId, scene.name(), itemKey)
                .map(this::toVO)
                .orElseThrow(() -> new IllegalStateException("用户偏好保存后未找到"));
    }

    private UserPreferenceScene requireScene(UserPreferenceScene scene) {
        if (scene == null) throw new IllegalArgumentException("scene 不能为空");
        return scene;
    }

    private UserPreferenceScene requireFavoriteScene(UserPreferenceScene scene) {
        UserPreferenceScene value = requireScene(scene);
        if (value != UserPreferenceScene.PRODUCT_MENU) {
            throw new IllegalArgumentException("当前偏好场景不支持收藏操作");
        }
        return value;
    }

    private UserPreferenceScene requireUsageScene(UserPreferenceScene scene) {
        UserPreferenceScene value = requireScene(scene);
        if (value != UserPreferenceScene.DATASOURCE_CREATE_TYPE) {
            throw new IllegalArgumentException("当前偏好场景不支持使用记录");
        }
        return value;
    }

    private String requireItemKey(String itemKey) {
        String key = StringUtils.trimToNull(itemKey);
        if (key == null) throw new IllegalArgumentException("itemKey 不能为空");
        if (key.length() > UserPreferenceConstants.ITEM_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("itemKey 长度不能超过 " + UserPreferenceConstants.ITEM_KEY_MAX_LENGTH);
        }
        return key;
    }

    private String requireUserId(String userId) {
        String id = StringUtils.trimToNull(userId);
        if (id == null) throw new IllegalArgumentException("userId 不能为空");
        return id;
    }

    private UserPreferenceVO toVO(UserPreferenceEntity source) {
        UserPreferenceVO target = BeanCopyUtils.copy(source, UserPreferenceVO.class, "scene");
        target.setScene(UserPreferenceScene.valueOf(source.getScene()));
        return target;
    }
}
