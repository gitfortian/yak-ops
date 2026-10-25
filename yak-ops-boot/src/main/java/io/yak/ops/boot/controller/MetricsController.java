package io.yak.ops.boot.controller;

import io.yak.ops.business.modeling.metric.MetricAtomicService;
import io.yak.ops.business.modeling.metric.MetricCompositeService;
import io.yak.ops.business.modeling.metric.MetricDerivedService;
import io.yak.ops.business.modeling.metric.MetricModifierService;
import io.yak.ops.business.modeling.metric.MetricTimePeriodService;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricAtomicUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricCompositeUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricDerivedUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricModifierInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricModifierUpdateDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricTimePeriodInputDTO;
import io.yak.ops.common.bean.dto.modeling.metric.MetricTimePeriodUpdateDTO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricAtomicVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricCompositeVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricDerivedVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricModifierVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricReferencesVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricSavedVO;
import io.yak.ops.common.bean.vo.modeling.metric.MetricTimePeriodVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.page.PagingData;
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

/**
 * Authenticated HTTP assembly for the five metric kinds, their availability checks and their
 * references.
 */
@RestController
@Validated
@RequestMapping("/api/v1/data-modeling/metrics")
public class MetricsController {
    @Resource
    private MetricTimePeriodService periods;

    @Resource
    private MetricModifierService modifiers;

    @Resource
    private MetricAtomicService atomics;

    @Resource
    private MetricDerivedService derived;

    @Resource
    private MetricCompositeService composites;

    @Resource
    private AuthenticationManager authentication;

    private String workspace() {
        return WorkspaceContext.requireWorkspaceId();
    }

    private String actor() {
        return authentication.getLoginUserId();
    }

    @GetMapping("/time-periods")
    public Result<PagingData<MetricTimePeriodVO>> periodPage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(PagingData.from(periods.page(workspace(), actor(), keyword, pageNo, pageSize)));
    }

    @GetMapping("/time-periods/{id}")
    public Result<MetricTimePeriodVO> period(@PathVariable String id) {
        return Result.success(periods.detail(workspace(), actor(), id));
    }

    @PostMapping("/time-periods")
    public Result<MetricSavedVO> createPeriod(@Valid @RequestBody MetricTimePeriodInputDTO input) {
        return Result.success(periods.create(workspace(), actor(), input));
    }

    @PutMapping("/time-periods/{id}")
    public Result<MetricSavedVO> updatePeriod(
            @PathVariable String id, @Valid @RequestBody MetricTimePeriodUpdateDTO input) {
        return Result.success(periods.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/time-periods/{id}")
    public Result<Boolean> deletePeriod(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        periods.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/time-periods/codes/availability")
    public Result<Map<String, Boolean>> periodCodeAvailable(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", periods.available(workspace(), actor(), enCode, excludeId)));
    }

    @GetMapping("/time-periods/{id}/references")
    public Result<MetricReferencesVO> periodReferences(@PathVariable String id) {
        return Result.success(periods.references(workspace(), actor(), id));
    }

    @GetMapping("/modifiers")
    public Result<PagingData<MetricModifierVO>> modifierPage(
            @RequestParam(required = false) String domainId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(
                PagingData.from(modifiers.page(workspace(), actor(), domainId, keyword, pageNo, pageSize)));
    }

    @GetMapping("/modifiers/{id}")
    public Result<MetricModifierVO> modifier(@PathVariable String id) {
        return Result.success(modifiers.detail(workspace(), actor(), id));
    }

    @PostMapping("/modifiers")
    public Result<MetricSavedVO> createModifier(@Valid @RequestBody MetricModifierInputDTO input) {
        return Result.success(modifiers.create(workspace(), actor(), input));
    }

    @PutMapping("/modifiers/{id}")
    public Result<MetricSavedVO> updateModifier(
            @PathVariable String id, @Valid @RequestBody MetricModifierUpdateDTO input) {
        return Result.success(modifiers.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/modifiers/{id}")
    public Result<Boolean> deleteModifier(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        modifiers.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/modifiers/codes/availability")
    public Result<Map<String, Boolean>> modifierCodeAvailable(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", modifiers.available(workspace(), actor(), enCode, excludeId)));
    }

    @GetMapping("/modifiers/{id}/references")
    public Result<MetricReferencesVO> modifierReferences(@PathVariable String id) {
        return Result.success(modifiers.references(workspace(), actor(), id));
    }

    @GetMapping("/atomic-metrics")
    public Result<PagingData<MetricAtomicVO>> atomicPage(
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(
                PagingData.from(atomics.page(workspace(), actor(), processId, keyword, pageNo, pageSize)));
    }

    @GetMapping("/atomic-metrics/{id}")
    public Result<MetricAtomicVO> atomic(@PathVariable String id) {
        return Result.success(atomics.detail(workspace(), actor(), id));
    }

    @PostMapping("/atomic-metrics")
    public Result<MetricSavedVO> createAtomic(@Valid @RequestBody MetricAtomicInputDTO input) {
        return Result.success(atomics.create(workspace(), actor(), input));
    }

    @PutMapping("/atomic-metrics/{id}")
    public Result<MetricSavedVO> updateAtomic(
            @PathVariable String id, @Valid @RequestBody MetricAtomicUpdateDTO input) {
        return Result.success(atomics.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/atomic-metrics/{id}")
    public Result<Boolean> deleteAtomic(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        atomics.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/atomic-metrics/codes/availability")
    public Result<Map<String, Boolean>> atomicCodeAvailable(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", atomics.available(workspace(), actor(), enCode, excludeId)));
    }

    @GetMapping("/atomic-metrics/{id}/references")
    public Result<MetricReferencesVO> atomicReferences(@PathVariable String id) {
        return Result.success(atomics.references(workspace(), actor(), id));
    }

    @GetMapping("/derived-metrics")
    public Result<PagingData<MetricDerivedVO>> derivedPage(
            @RequestParam(required = false) String processId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(
                PagingData.from(derived.page(workspace(), actor(), processId, keyword, pageNo, pageSize)));
    }

    @GetMapping("/derived-metrics/{id}")
    public Result<MetricDerivedVO> derivedMetric(@PathVariable String id) {
        return Result.success(derived.detail(workspace(), actor(), id));
    }

    @PostMapping("/derived-metrics")
    public Result<MetricSavedVO> createDerived(@Valid @RequestBody MetricDerivedInputDTO input) {
        return Result.success(derived.create(workspace(), actor(), input));
    }

    @PutMapping("/derived-metrics/{id}")
    public Result<MetricSavedVO> updateDerived(
            @PathVariable String id, @Valid @RequestBody MetricDerivedUpdateDTO input) {
        return Result.success(derived.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/derived-metrics/{id}")
    public Result<Boolean> deleteDerived(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        derived.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/derived-metrics/codes/availability")
    public Result<Map<String, Boolean>> derivedCodeAvailable(
            @RequestParam String atomicMetricId,
            @RequestParam String periodId,
            @RequestParam(required = false) List<String> modifierIds,
            @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of(
                "available",
                derived.available(workspace(), actor(), atomicMetricId, periodId, modifierIds, excludeId)));
    }

    @GetMapping("/derived-metrics/{id}/references")
    public Result<MetricReferencesVO> derivedReferences(@PathVariable String id) {
        return Result.success(derived.references(workspace(), actor(), id));
    }

    @GetMapping("/composite-metrics")
    public Result<PagingData<MetricCompositeVO>> compositePage(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "参数必须大于零") int pageNo,
            @RequestParam(defaultValue = "20")
                    @Min(value = 1, message = "参数必须大于零")
                    @Max(value = 100, message = "每页最多100条")
                    int pageSize) {
        return Result.success(PagingData.from(composites.page(workspace(), actor(), keyword, pageNo, pageSize)));
    }

    @GetMapping("/composite-metrics/{id}")
    public Result<MetricCompositeVO> composite(@PathVariable String id) {
        return Result.success(composites.detail(workspace(), actor(), id));
    }

    @PostMapping("/composite-metrics")
    public Result<MetricSavedVO> createComposite(@Valid @RequestBody MetricCompositeInputDTO input) {
        return Result.success(composites.create(workspace(), actor(), input));
    }

    @PutMapping("/composite-metrics/{id}")
    public Result<MetricSavedVO> updateComposite(
            @PathVariable String id, @Valid @RequestBody MetricCompositeUpdateDTO input) {
        return Result.success(composites.update(workspace(), actor(), id, input));
    }

    @DeleteMapping("/composite-metrics/{id}")
    public Result<Boolean> deleteComposite(
            @PathVariable String id, @RequestParam @Min(value = 1, message = "参数必须大于零") long revision) {
        composites.delete(workspace(), actor(), id, revision);
        return Result.success(true);
    }

    @GetMapping("/composite-metrics/codes/availability")
    public Result<Map<String, Boolean>> compositeCodeAvailable(
            @RequestParam String enCode, @RequestParam(required = false) String excludeId) {
        return Result.success(Map.of("available", composites.available(workspace(), actor(), enCode, excludeId)));
    }
}
