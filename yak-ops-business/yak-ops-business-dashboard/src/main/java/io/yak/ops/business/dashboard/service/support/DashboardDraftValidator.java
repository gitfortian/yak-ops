package io.yak.ops.business.dashboard.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.analysis.AnalysisReferenceService;
import io.yak.ops.business.dashboard.domain.DashboardDraft;
import io.yak.ops.business.dashboard.domain.FilterBindingSpec;
import io.yak.ops.business.dashboard.domain.GlobalFilterSpec;
import io.yak.ops.business.dashboard.domain.InteractionSpec;
import io.yak.ops.business.dashboard.domain.WidgetSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Dashboard 草稿业务约束校验与标准化。 */
@Component
@RequiredArgsConstructor
public class DashboardDraftValidator {

    private static final int MAX_THEME_JSON = 16000;
    private static final int MAX_WIDGETS = 200;
    private static final int MAX_INLINE_JSON = 65535;
    private static final int MAX_GLOBAL_FILTERS = 20;
    private static final int MAX_FILTER_BINDINGS = 200;
    private static final int MAX_INTERACTIONS = 100;
    private static final int MAX_DEFAULT_VALUE_JSON = 4000;

    private final AnalysisReferenceService analysisReferences;
    private final ObjectMapper objectMapper;

    public DashboardDraft normalize(DashboardDraft draft) {
        Objects.requireNonNull(draft, "draft");

        String name = required(draft.name(), "Dashboard 名称", 200);
        String description = optional(draft.description(), "Dashboard 描述", 2000);
        Long activeDatasetId = draft.activeDatasetId();
        if (activeDatasetId != null && activeDatasetId <= 0L) {
            activeDatasetId = null;
        }

        Object theme = validateObject(draft.theme(), "Dashboard Theme", MAX_THEME_JSON);
        WidgetNormalization widgetNormalization = normalizeWidgets(draft.widgets());
        FilterNormalization filterNormalization = normalizeGlobalFilters(
                draft.globalFilters(),
                widgetNormalization.widgetKeys());
        List<InteractionSpec> interactions = normalizeInteractions(
                draft.interactions(),
                widgetNormalization.widgetKeys(),
                filterNormalization.filterKeys());

        return new DashboardDraft(
                name,
                description,
                activeDatasetId,
                theme,
                widgetNormalization.widgets(),
                filterNormalization.filters(),
                interactions);
    }

    private WidgetNormalization normalizeWidgets(List<WidgetSpec> values) {
        List<WidgetSpec> source = values == null ? List.of() : values;
        if (source.size() > MAX_WIDGETS) {
            throw new IllegalArgumentException("Dashboard 组件不能超过 " + MAX_WIDGETS + " 个");
        }

        List<WidgetSpec> widgets = new ArrayList<>(source.size());
        Set<String> widgetKeys = new HashSet<>();
        for (WidgetSpec value : source) {
            if (value == null) {
                throw new IllegalArgumentException("DashboardWidget 不能为空");
            }

            String widgetKey = required(value.widgetKey(), "widgetKey", 64);
            if (!widgetKeys.add(widgetKey)) {
                throw new IllegalArgumentException("widgetKey 重复：" + widgetKey);
            }

            String title = optional(value.title(), "Widget 标题", 200);
            boolean linked = value.analysisId() != null;
            boolean inline = value.inlineAnalysis() != null;
            if (linked == inline) {
                throw new IllegalArgumentException(
                        "Widget 必须且只能选择 analysisId 或 inlineAnalysis：" + widgetKey);
            }

            if (linked) {
                if (value.analysisId() <= 0L) {
                    throw new IllegalArgumentException("analysisId 必须大于 0");
                }
                analysisReferences.requireExists(value.analysisId());
            }

            validateLayout(value, widgetKey);
            Object inlineAnalysis = inline
                    ? validateObject(value.inlineAnalysis(), "inlineAnalysis：" + widgetKey, MAX_INLINE_JSON)
                    : null;

            widgets.add(new WidgetSpec(
                    widgetKey,
                    value.analysisId(),
                    title,
                    inlineAnalysis,
                    value.x(),
                    value.y(),
                    value.w(),
                    value.h(),
                    value.minW(),
                    value.minH()));
        }

        return new WidgetNormalization(List.copyOf(widgets), Set.copyOf(widgetKeys));
    }

    private FilterNormalization normalizeGlobalFilters(
            List<GlobalFilterSpec> values,
            Set<String> widgetKeys) {
        List<GlobalFilterSpec> source = values == null ? List.of() : values;
        if (source.size() > MAX_GLOBAL_FILTERS) {
            throw new IllegalArgumentException(
                    "Dashboard 全局筛选器不能超过 " + MAX_GLOBAL_FILTERS + " 个");
        }

        List<GlobalFilterSpec> filters = new ArrayList<>(source.size());
        Set<String> filterKeys = new HashSet<>();
        int bindingCount = 0;

        for (GlobalFilterSpec value : source) {
            if (value == null) {
                throw new IllegalArgumentException("Dashboard 全局筛选器不能为空");
            }

            String filterKey = required(value.filterKey(), "filterKey", 64);
            if (!filterKeys.add(filterKey)) {
                throw new IllegalArgumentException("filterKey 重复：" + filterKey);
            }

            String filterName = required(value.name(), "筛选器名称", 200);
            var operator = Objects.requireNonNull(value.operator(), "筛选器 operator");
            Object defaultValue = validateScalar(
                    value.defaultValue(),
                    "全局筛选器默认值：" + filterKey,
                    MAX_DEFAULT_VALUE_JSON);

            List<FilterBindingSpec> sourceBindings =
                    value.bindings() == null ? List.of() : value.bindings();
            List<FilterBindingSpec> bindings = new ArrayList<>(sourceBindings.size());
            Set<String> boundWidgets = new HashSet<>();

            for (FilterBindingSpec binding : sourceBindings) {
                if (binding == null) {
                    throw new IllegalArgumentException("筛选器绑定不能为空：" + filterKey);
                }

                String widgetKey = required(binding.widgetKey(), "筛选器 widgetKey", 64);
                if (!widgetKeys.contains(widgetKey)) {
                    throw new IllegalArgumentException("筛选器绑定的 Widget 不存在：" + widgetKey);
                }
                if (!boundWidgets.add(widgetKey)) {
                    throw new IllegalArgumentException(
                            "同一筛选器对单个 Widget 只能绑定一个字段：" + widgetKey);
                }

                String fieldId = required(binding.fieldId(), "筛选器 fieldId", 64);
                bindings.add(new FilterBindingSpec(widgetKey, fieldId));

                bindingCount++;
                if (bindingCount > MAX_FILTER_BINDINGS) {
                    throw new IllegalArgumentException(
                            "Dashboard 筛选器字段映射不能超过 " + MAX_FILTER_BINDINGS + " 个");
                }
            }

            filters.add(new GlobalFilterSpec(
                    filterKey,
                    filterName,
                    operator,
                    defaultValue,
                    List.copyOf(bindings)));
        }

        return new FilterNormalization(List.copyOf(filters), Set.copyOf(filterKeys));
    }

    private List<InteractionSpec> normalizeInteractions(
            List<InteractionSpec> values,
            Set<String> widgetKeys,
            Set<String> filterKeys) {
        List<InteractionSpec> source = values == null ? List.of() : values;
        if (source.size() > MAX_INTERACTIONS) {
            throw new IllegalArgumentException(
                    "Dashboard 联动规则不能超过 " + MAX_INTERACTIONS + " 个");
        }

        List<InteractionSpec> result = new ArrayList<>(source.size());
        Set<String> interactionKeys = new HashSet<>();

        for (InteractionSpec value : source) {
            if (value == null) {
                throw new IllegalArgumentException("Dashboard 联动规则不能为空");
            }

            String interactionKey = required(value.interactionKey(), "interactionKey", 64);
            if (!interactionKeys.add(interactionKey)) {
                throw new IllegalArgumentException("interactionKey 重复：" + interactionKey);
            }

            var event = Objects.requireNonNull(value.event(), "联动 event");
            String sourceWidgetKey =
                    required(value.sourceWidgetKey(), "联动 sourceWidgetKey", 64);
            if (!widgetKeys.contains(sourceWidgetKey)) {
                throw new IllegalArgumentException("联动来源 Widget 不存在：" + sourceWidgetKey);
            }

            String sourceFieldId = required(value.sourceFieldId(), "联动 sourceFieldId", 64);
            String targetFilterKey =
                    required(value.targetFilterKey(), "联动 targetFilterKey", 64);
            if (!filterKeys.contains(targetFilterKey)) {
                throw new IllegalArgumentException("联动目标筛选器不存在：" + targetFilterKey);
            }

            result.add(new InteractionSpec(
                    interactionKey,
                    event,
                    sourceWidgetKey,
                    sourceFieldId,
                    targetFilterKey));
        }

        return List.copyOf(result);
    }

    private void validateLayout(WidgetSpec value, String widgetKey) {
        if (value.x() < 0 || value.x() >= 24) {
            throw new IllegalArgumentException("Widget x 必须在 0~23：" + widgetKey);
        }
        if (value.y() < 0) {
            throw new IllegalArgumentException("Widget y 不能小于 0：" + widgetKey);
        }
        if (value.w() <= 0 || value.w() > 24) {
            throw new IllegalArgumentException("Widget w 必须在 1~24：" + widgetKey);
        }
        if (value.h() <= 0 || value.h() > 60) {
            throw new IllegalArgumentException("Widget h 必须在 1~60：" + widgetKey);
        }
        if (value.x() + value.w() > 24) {
            throw new IllegalArgumentException("Widget 超出 24 栅格：" + widgetKey);
        }
        if (value.minW() != null && (value.minW() <= 0 || value.minW() > value.w())) {
            throw new IllegalArgumentException(
                    "Widget minW 必须大于 0 且不能超过 w：" + widgetKey);
        }
        if (value.minH() != null && (value.minH() <= 0 || value.minH() > value.h())) {
            throw new IllegalArgumentException(
                    "Widget minH 必须大于 0 且不能超过 h：" + widgetKey);
        }
    }

    private Object validateObject(Object value, String label, int maxLength) {
        if (value == null) {
            return null;
        }

        JsonNode node = objectMapper.valueToTree(value);
        if (!node.isObject()) {
            throw new IllegalArgumentException(label + " 必须是 JSON 对象");
        }
        ensureJsonLength(value, label, maxLength);
        return value;
    }

    private Object validateScalar(Object value, String label, int maxLength) {
        if (value == null) {
            return null;
        }

        JsonNode node = objectMapper.valueToTree(value);
        if (!node.isValueNode()) {
            throw new IllegalArgumentException(label + " 必须是标量");
        }
        ensureJsonLength(value, label, maxLength);
        return value;
    }

    private void ensureJsonLength(Object value, String label, int maxLength) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (json.length() > maxLength) {
                throw new IllegalArgumentException(label + " 配置过大");
            }
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(label + " 无法序列化", exception);
        }
    }

    private String required(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String optional(String value, String label, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    label + "不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private record WidgetNormalization(
            List<WidgetSpec> widgets,
            Set<String> widgetKeys) {
    }

    private record FilterNormalization(
            List<GlobalFilterSpec> filters,
            Set<String> filterKeys) {
    }
}
