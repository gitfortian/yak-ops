package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.relation.ModelRelationService;
import io.yak.ops.common.bean.dto.modeling.CreateModelRelationDTO;
import io.yak.ops.common.bean.dto.modeling.RelationProposalQueryDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateFieldSynonymsDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelGrainDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelRelationDTO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationProposalVO;
import io.yak.ops.common.bean.vo.modeling.ModelRelationVO;
import io.yak.ops.common.bean.vo.modeling.SemanticExportVO;
import io.yak.ops.business.modeling.semantic.OssieSemanticExporter;
import io.yak.ops.business.modeling.synonym.SemanticSynonymService;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated HTTP assembly for the semantic model: relations with key proposals, the model
 * grain, model-field synonyms and the read-only Ossie projection.
 */
@RestController
@Validated
public class ModelRelationController {
    @Resource
    private ModelRelationService relationService;

    @Resource
    private SemanticSynonymService synonymService;

    @Resource
    private OssieSemanticExporter exporter;

    @Resource
    private AuthenticationManager authenticationManager;

    @GetMapping("/api/v1/data-modeling/models/{modelId}/relations")
    public Result<List<ModelRelationVO>> relations(@PathVariable String modelId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(relationService.list(workspaceId, actor(), modelId));
    }

    @PostMapping("/api/v1/data-modeling/models/{modelId}/relations")
    public Result<ModelRelationVO> create(
            @PathVariable String modelId, @Valid @RequestBody CreateModelRelationDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(relationService.create(workspaceId, actor(), modelId, dto));
    }

    @PostMapping("/api/v1/data-modeling/models/{modelId}/relations/proposals")
    public Result<List<ModelRelationProposalVO>> proposals(
            @PathVariable String modelId, @Valid @RequestBody RelationProposalQueryDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(relationService.proposals(workspaceId, actor(), modelId, dto.getTargetModelId()));
    }

    @PutMapping("/api/v1/data-modeling/relations/{relationId}")
    public Result<ModelRelationVO> update(
            @PathVariable String relationId, @Valid @RequestBody UpdateModelRelationDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(relationService.update(workspaceId, actor(), relationId, dto));
    }

    @DeleteMapping("/api/v1/data-modeling/relations/{relationId}")
    public Result<Boolean> delete(@PathVariable String relationId, Long revision) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        relationService.delete(workspaceId, actor(), relationId, revision == null ? 0L : revision);
        return Result.success(Boolean.TRUE);
    }

    @PutMapping("/api/v1/data-modeling/models/{modelId}/grain")
    public Result<Boolean> updateGrain(@PathVariable String modelId, @Valid @RequestBody UpdateModelGrainDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        relationService.updateGrain(workspaceId, actor(), modelId, dto);
        return Result.success(Boolean.TRUE);
    }

    @GetMapping("/api/v1/data-modeling/models/{modelId}/fields/{fieldCode}/synonyms")
    public Result<List<String>> fieldSynonyms(@PathVariable String modelId, @PathVariable String fieldCode) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(synonymService.listForModelField(workspaceId, actor(), modelId, fieldCode));
    }

    @PutMapping("/api/v1/data-modeling/models/{modelId}/fields/{fieldCode}/synonyms")
    public Result<List<String>> replaceFieldSynonyms(
            @PathVariable String modelId,
            @PathVariable String fieldCode,
            @Valid @RequestBody UpdateFieldSynonymsDTO dto) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        synonymService.replaceForModelField(workspaceId, actor(), modelId, fieldCode, dto.getTerms());
        return Result.success(synonymService.listForModelField(workspaceId, actor(), modelId, fieldCode));
    }

    @PostMapping("/api/v1/data-modeling/models/{modelId}/semantic-export")
    public Result<SemanticExportVO> export(@PathVariable String modelId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        return Result.success(exporter.export(workspaceId, actor(), modelId));
    }

    private String actor() {
        return authenticationManager.getLoginUserId();
    }
}
