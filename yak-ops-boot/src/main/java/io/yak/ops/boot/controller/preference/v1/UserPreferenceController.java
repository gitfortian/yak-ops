package io.yak.ops.boot.controller.preference.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.preference.UserPreferenceService;
import io.yak.ops.preference.constant.UserPreferenceConstants;
import io.yak.ops.common.bean.dto.preference.UserPreferenceFavoriteDTO;
import io.yak.ops.common.bean.vo.preference.UserPreferenceVO;
import io.yak.ops.common.enums.preference.UserPreferenceScene;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外提供当前认证用户的偏好查询、收藏变更和使用记录接口。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Tag(name = "用户偏好接口")
@RestController
@RequestMapping(UserPreferenceConstants.API_PREFIX)
public class UserPreferenceController {

    @Resource
    private UserPreferenceService userPreferenceService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Operation(summary = "查询当前用户指定场景的偏好")
    @GetMapping
    public Result<List<UserPreferenceVO>> list(@RequestParam("scene") UserPreferenceScene scene) {
        return Result.success(userPreferenceService.queryPreferences(scene, currentUserId()));
    }

    @Operation(summary = "更新当前用户的收藏状态")
    @PutMapping("/{scene}/{itemKey}/favorite")
    public Result<UserPreferenceVO> updateFavorite(
            @PathVariable("scene") UserPreferenceScene scene,
            @PathVariable("itemKey") String itemKey,
            @Valid @RequestBody UserPreferenceFavoriteDTO dto) {
        return Result.success(userPreferenceService.updateFavorite(scene, itemKey, dto, currentUserId()));
    }

    @Operation(summary = "记录当前用户的使用行为")
    @PostMapping("/{scene}/{itemKey}/use")
    public Result<UserPreferenceVO> recordUsage(
            @PathVariable("scene") UserPreferenceScene scene, @PathVariable("itemKey") String itemKey) {
        return Result.success(userPreferenceService.recordUsage(scene, itemKey, currentUserId()));
    }

    private String currentUserId() {
        return authenticationManager.getLoginUserId();
    }
}
