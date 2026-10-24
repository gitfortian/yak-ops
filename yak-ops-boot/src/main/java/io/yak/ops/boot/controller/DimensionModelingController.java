package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.dimension.DimensionService;
import io.yak.ops.business.modeling.model.ModelService;
import io.yak.ops.business.modeling.publication.PublicationService;
import io.yak.ops.common.bean.dto.modeling.DimensionInputDTO;
import io.yak.ops.common.bean.dto.modeling.ModelInputDTO;
import io.yak.ops.common.bean.dto.modeling.PublicationTargetDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateDimensionDTO;
import io.yak.ops.common.bean.dto.modeling.UpdateModelDTO;
import io.yak.ops.common.bean.vo.modeling.DimensionVO;
import io.yak.ops.common.bean.vo.modeling.MetadataSnapshotVO;
import io.yak.ops.common.bean.vo.modeling.ModelVO;
import io.yak.ops.common.bean.vo.modeling.PublicationPreviewVO;
import io.yak.ops.common.bean.vo.modeling.PublicationVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.dimension.DimensionScope;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import io.yak.ops.security.authentication.AuthenticationManager;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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

/** Authenticated HTTP boundary for Workspace concept dimensions and logical models. */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/dimension-modeling")
public class DimensionModelingController {
    @Resource
    private DimensionService dimensions;

    @Resource
    private ModelService models;

    @Resource
    private PublicationService publications;

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

    @GetMapping("/models")
    public Result<PagingData<ModelVO>> models(
            @RequestParam(required = false) ModelType type,
            @RequestParam(required = false) String layerId,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String domainId,
            @RequestParam(required = false) String datamartId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return Result.success(PagingData.from(models.page(
                WorkspaceContext.requireWorkspaceId(),
                authentication.getLoginUserId(),
                type,
                layerId,
                ownerId,
                domainId,
                datamartId,
                keyword,
                pageNo,
                pageSize)));
    }

    @GetMapping("/models/name-availability")
    public Result<Boolean> nameAvailable(
            @RequestParam String layerId,
            @RequestParam String nameSuffix,
            @RequestParam(required = false) String excludeId) {
        return Result.success(models.nameAvailable(
                WorkspaceContext.requireWorkspaceId(),
                authentication.getLoginUserId(),
                layerId,
                nameSuffix,
                excludeId));
    }

    @GetMapping("/models/{id}")
    public Result<ModelVO> model(@PathVariable String id) {
        return Result.success(
                models.detail(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id));
    }

    @PostMapping("/models")
    public Result<ModelVO> createModel(@Valid @RequestBody ModelInputDTO input) {
        return Result.success(
                models.create(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), input));
    }

    @PutMapping("/models/{id}")
    public Result<ModelVO> updateModel(@PathVariable String id, @Valid @RequestBody UpdateModelDTO input) {
        return Result.success(
                models.update(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id, input));
    }

    @DeleteMapping("/models/{id}")
    public Result<Boolean> deleteModel(@PathVariable String id, @RequestParam @Min(1) long revision) {
        models.delete(WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), id, revision);
        return Result.success(true);
    }

    @PostMapping("/models/{modelId}/publications/preview")
    public Result<PublicationPreviewVO> previewPublication(
            @PathVariable String modelId, @Valid @RequestBody PublicationTargetDTO target) {
        return Result.success(publications.preview(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), modelId, target));
    }

    @PostMapping("/models/{modelId}/publications")
    public Result<PublicationVO> publish(
            @PathVariable String modelId, @Valid @RequestBody PublicationTargetDTO target) {
        return Result.success(publications.publish(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), modelId, target));
    }

    @GetMapping("/models/{modelId}/publications")
    public Result<List<PublicationVO>> publicationHistory(@PathVariable String modelId) {
        return Result.success(publications.history(WorkspaceContext.requireWorkspaceId(), modelId));
    }

    @GetMapping("/publications/{publicationId}")
    public Result<PublicationVO> publication(@PathVariable String publicationId) {
        return Result.success(publications.detail(WorkspaceContext.requireWorkspaceId(), publicationId));
    }

    @PostMapping("/publications/{publicationId}/reconcile")
    public Result<PublicationVO> reconcilePublication(@PathVariable String publicationId) {
        return Result.success(publications.reconcile(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), publicationId));
    }

    @GetMapping("/publications/{publicationId}/metadata")
    public Result<MetadataSnapshotVO> publicationMetadata(@PathVariable String publicationId) {
        return Result.success(publications.metadata(WorkspaceContext.requireWorkspaceId(), publicationId));
    }

    @PostMapping("/publications/{publicationId}/metadata/refresh")
    public Result<MetadataSnapshotVO> refreshPublicationMetadata(@PathVariable String publicationId) {
        return Result.success(publications.refreshMetadata(
                WorkspaceContext.requireWorkspaceId(), authentication.getLoginUserId(), publicationId));
    }
}
