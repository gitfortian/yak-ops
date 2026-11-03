package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.synonym.SemanticSynonymService;
import io.yak.ops.common.bean.dto.modeling.UpdateModelSynonymsDTO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.modeling.SemanticSynonymTargetType;
import io.yak.ops.common.enums.modeling.dimension.DimensionModelingErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP assembly for synonyms of data standards and metrics. */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/synonyms")
public class SemanticSynonymController {
    @Resource
    private SemanticSynonymService service;

    @Resource
    private AuthenticationManager authenticationManager;

    @GetMapping
    public Result<List<String>> list(@RequestParam String targetType, @RequestParam String targetId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(service.list(workspaceId, actor(), targetType(targetType), targetId));
    }

    @PutMapping
    public Result<List<String>> replace(@Valid @RequestBody UpdateModelSynonymsDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        SemanticSynonymTargetType type = targetType(dto.getTargetType());
        service.replace(workspaceId, actor(), type, dto.getTargetId(), dto.getTerms());
        return Result.success(service.list(workspaceId, actor(), type, dto.getTargetId()));
    }

    private String actor() {
        return authenticationManager.getLoginUserId();
    }

    private static SemanticSynonymTargetType targetType(String raw) {
        try {
            return SemanticSynonymTargetType.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException unknown) {
            throw new BusinessException(DimensionModelingErrorCode.SYNONYM_TARGET_INVALID);
        }
    }
}
