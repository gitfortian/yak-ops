package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.PlanningService;
import io.yak.ops.common.bean.dto.modeling.NodeInputDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateNodeDTO;
import io.yak.ops.common.bean.vo.modeling.AssociatedModelsVO;
import io.yak.ops.common.bean.vo.modeling.OwnerOptionVO;
import io.yak.ops.common.bean.vo.modeling.PlanningNodeVO;
import io.yak.ops.common.bean.vo.modeling.SavedNodeVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.modeling.NodeKind;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
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

/** Authenticated HTTP boundary for the Workspace-owned planning hierarchy. */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/warehouse-planning")
public class WarehousePlanningController {
    @Resource
    private PlanningService service;

    @Resource
    private AuthenticationManager authentication;

    @GetMapping("/tree")
    public Result<List<PlanningNodeVO>> tree() {
        return Result.success(service.tree(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId()));
    }

    @GetMapping("/owners")
    public Result<List<OwnerOptionVO>> owners() {
        return Result.success(service.owners(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId()));
    }

    @GetMapping("/codes/availability")
    public Result<Map<String, Boolean>> availability(
            @RequestParam String code,
            @RequestParam(required = false) NodeKind excludeKind,
            @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of(
                "available",
                service.available(
                        WorkspaceContext.requireWorkspaceId(),
                        authentication.getLoginUserId(),
                        code,
                        excludeKind,
                        excludeId)));
    }

    @PostMapping("/categories")
    public Result<SavedNodeVO> category(@Valid @RequestBody NodeInputDTO input) {
        return create(NodeKind.CATEGORY, null, input);
    }

    @PostMapping("/categories/{id}/domains")
    public Result<SavedNodeVO> domain(@PathVariable String id, @Valid @RequestBody NodeInputDTO input) {
        return create(NodeKind.DOMAIN, id, input);
    }

    @PostMapping("/domains/{id}/processes")
    public Result<SavedNodeVO> process(@PathVariable String id, @Valid @RequestBody NodeInputDTO input) {
        return create(NodeKind.PROCESS, id, input);
    }

    @PutMapping("/{level:categories|domains|processes}/{id}")
    public Result<SavedNodeVO> update(
            @PathVariable String level, @PathVariable String id, @Valid @RequestBody UpdateNodeDTO input) {
        return Result.success(service.update(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), kind(level), id, input));
    }

    @DeleteMapping("/{level:categories|domains|processes}/{id}")
    public Result<Boolean> delete(
            @PathVariable String level, @PathVariable String id, @RequestParam @Min(1) long revision) {
        service.delete(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), kind(level), id, revision);
        return Result.success(true);
    }

    @GetMapping("/processes/{id}/logical-models")
    public Result<AssociatedModelsVO> models(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(
                service.associatedModels(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id));
    }

    private Result<SavedNodeVO> create(NodeKind kind, String parentId, NodeInputDTO input) {
        return Result.success(service.create(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), kind, parentId, input));
    }

    private static NodeKind kind(String level) {
        return switch (level) {
            case "categories" -> NodeKind.CATEGORY;
            case "domains" -> NodeKind.DOMAIN;
            case "processes" -> NodeKind.PROCESS;
            default -> throw new IllegalArgumentException("Unknown planning resource");
        };
    }
}
