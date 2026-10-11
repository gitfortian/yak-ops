package io.yak.ops.boot.controller.task.v1;

import io.yak.ops.business.task.definition.DefinitionService;
import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.DefinitionQueryDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.plugin.task.api.ParameterFormat;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Canonical Task Definition API; all writes share one plugin-independent business transaction. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/task-definitions")
public class TaskDefinitionController {
    @Resource
    private DefinitionService service;

    @Resource
    private AuthenticationManager authenticationManager;

    @Resource
    private TaskPluginRegistry taskPluginRegistry;

    @PostMapping
    public Result<DefinitionVO> create(@Valid @RequestBody CreateDefinitionDTO dto) {
        return Result.success(service.create(dto, authenticationManager.getLoginUserId()));
    }

    @PutMapping("/{id}")
    public Result<DefinitionVO> update(@PathVariable String id, @Valid @RequestBody UpdateDefinitionDTO dto) {
        return Result.success(service.update(id, dto, authenticationManager.getLoginUserId()));
    }

    @GetMapping("/{id}")
    public Result<DefinitionVO> detail(@PathVariable String id) {
        return Result.success(service.query(id));
    }

    @PostMapping("/page")
    public Result<PagingData<DefinitionVO>> page(@Valid @RequestBody DefinitionQueryDTO dto) {
        return Result.success(service.queryPage(dto));
    }

    @GetMapping("/{id}/parameters/hocon")
    public Result<String> exportHocon(@PathVariable String id) {
        DefinitionVO definition = service.query(id);
        return Result.success(
                taskPluginRegistry.format(definition.getTaskType(), definition.getParameters(), ParameterFormat.HOCON));
    }

    @GetMapping("/{id}/versions")
    public Result<List<DefinitionVersionVO>> versions(@PathVariable String id) {
        return Result.success(service.queryVersions(id));
    }

    @PostMapping("/{id}/publish")
    public Result<DefinitionVO> publish(@PathVariable String id) {
        return Result.success(service.publish(id, authenticationManager.getLoginUserId()));
    }

    @PostMapping("/{id}/unpublish")
    public Result<DefinitionVO> unpublish(@PathVariable String id) {
        return Result.success(service.unpublish(id, authenticationManager.getLoginUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable String id) {
        service.delete(id, authenticationManager.getLoginUserId());
        return Result.success(true);
    }
}
