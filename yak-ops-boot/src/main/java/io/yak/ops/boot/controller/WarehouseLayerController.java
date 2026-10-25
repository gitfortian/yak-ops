package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.naming.NamingRuleService;
import io.yak.ops.business.modeling.planning.LayerService;
import io.yak.ops.common.bean.dto.modeling.LayerInputDTO;
import io.yak.ops.common.bean.dto.modeling.NamingRuleUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateLayerDTO;
import io.yak.ops.common.bean.vo.modeling.LayerDataSourceVO;
import io.yak.ops.common.bean.vo.modeling.LayerNamingRuleVO;
import io.yak.ops.common.bean.vo.modeling.LayerOptionVO;
import io.yak.ops.common.bean.vo.modeling.LayerVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
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

/** Authenticated HTTP boundary for the Workspace data warehouse layers. */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/warehouse-planning/layers")
public class WarehouseLayerController {
    @Resource
    private LayerService service;

    @Resource
    private NamingRuleService namingRules;

    @Resource
    private AuthenticationManager authentication;

    @GetMapping
    public Result<List<LayerVO>> list() {
        return Result.success(service.list(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId()));
    }

    @GetMapping("/options")
    public Result<List<LayerOptionVO>> options() {
        return Result.success(service.options(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId()));
    }

    @GetMapping("/datasources")
    public Result<List<LayerDataSourceVO>> datasources() {
        return Result.success(
                service.datasources(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId()));
    }

    @GetMapping("/codes/availability")
    public Result<Map<String, Boolean>> availability(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of(
                "available",
                service.available(
                        WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), enCode, excludeId)));
    }

    @PostMapping
    public Result<LayerVO> create(@Valid @RequestBody LayerInputDTO input) {
        return Result.success(
                service.create(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), input));
    }

    @PutMapping("/{id}")
    public Result<LayerVO> update(@PathVariable String id, @Valid @RequestBody UpdateLayerDTO input) {
        return Result.success(
                service.update(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id, input));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable String id, @RequestParam @Min(1) long revision) {
        service.delete(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/{id}/naming-rule")
    public Result<LayerNamingRuleVO> namingRule(@PathVariable String id) {
        return Result.success(
                namingRules.rule(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id));
    }

    @PutMapping("/{id}/naming-rule")
    public Result<LayerNamingRuleVO> updateNamingRule(
            @PathVariable String id, @Valid @RequestBody NamingRuleUpdateDTO input) {
        return Result.success(
                namingRules.update(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id, input));
    }
}
