package io.yak.ops.dao.mapper.preference;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.preference.UserPreferenceEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户偏好持久化 Mapper，负责收藏状态和使用信号的原子 Upsert。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreferenceEntity> {

    @Insert({
        "INSERT INTO yak_ops_user_preference (",
        "id, user_id, scene, item_key, favorite, sort_order, use_count, last_used_time,",
        "create_time, update_time, create_by, update_by",
        ") VALUES (",
        "#{id}, #{userId}, #{scene}, #{itemKey}, #{favorite}, #{sortOrder}, #{useCount}, #{lastUsedTime},",
        "#{createTime}, #{updateTime}, #{createBy}, #{updateBy}",
        ") ON DUPLICATE KEY UPDATE",
        "favorite = VALUES(favorite),",
        "sort_order = VALUES(sort_order),",
        "update_time = VALUES(update_time),",
        "update_by = VALUES(update_by)"
    })
    int upsertFavorite(UserPreferenceEntity entity);

    @Insert({
        "INSERT INTO yak_ops_user_preference (",
        "id, user_id, scene, item_key, favorite, sort_order, use_count, last_used_time,",
        "create_time, update_time, create_by, update_by",
        ") VALUES (",
        "#{id}, #{userId}, #{scene}, #{itemKey}, #{favorite}, #{sortOrder}, #{useCount}, #{lastUsedTime},",
        "#{createTime}, #{updateTime}, #{createBy}, #{updateBy}",
        ") ON DUPLICATE KEY UPDATE",
        "use_count = use_count + 1,",
        "last_used_time = VALUES(last_used_time),",
        "update_time = VALUES(update_time),",
        "update_by = VALUES(update_by)"
    })
    int recordUsage(UserPreferenceEntity entity);
}
