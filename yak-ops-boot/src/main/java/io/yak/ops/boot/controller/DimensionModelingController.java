package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.dimension.DimensionService;
import io.yak.ops.common.bean.dto.modeling.DimensionInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDimensionDTO;
import io.yak.ops.common.bean.vo.modeling.DimensionVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated HTTP boundary for Workspace concept dimensions. */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/dimension-modeling")
public class DimensionModelingController {
    @Resource
    private DimensionService dimensions;

    @Resource
    private AuthenticationManager authentication;

    @GetMapping("/dimensions")
    public Result<List<DimensionVO>> dimensions(
            @RequestParam(required = false) DimensionScope scope,
            @RequestParam(required = false) String domainId,
            @RequestParam(required = false) String datamartId) {
        return Result.success(dimensions.list(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), scope, domainId, datamartId));
    }

    @PostMapping("/dimensions")
    public Result<DimensionVO> create(@Valid @RequestBody DimensionInputDTO input) {
        return Result.success(
                dimensions.create(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), input));
    }

    @PutMapping("/dimensions/{id}")
    public Result<DimensionVO> update(@PathVariable String id, @Valid @RequestBody UpdateDimensionDTO input) {
        return Result.success(
                dimensions.update(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id, input));
    }

    @DeleteMapping("/dimensions/{id}")
    public Result<Boolean> delete(@PathVariable String id, @RequestParam @Min(1) long revision) {
        dimensions.delete(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id, revision);
        return Result.success(true);
    }
}
