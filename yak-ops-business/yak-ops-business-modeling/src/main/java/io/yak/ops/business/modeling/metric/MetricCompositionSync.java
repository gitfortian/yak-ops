package io.yak.ops.business.modeling.metric;

import io.yak.ops.common.enums.modeling.metric.MetricErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.metric.MetricDerivedEntity;
import io.yak.ops.dao.repository.modeling.metric.MetricDerivedRepository;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Keeps composed derived metrics consistent with their edited parts.
 *
 * <p>A derived metric carries no name of its own: its code and name are composed from the period,
 * the modifiers and the atomic metric, so editing one of those parts changes every derived metric
 * built on it. That is the intended behavior — the composition is what keeps the caliber registry
 * free of duplicates — and this component performs it inside the editing transaction. A recomposed
 * value that no longer fits its column limit, or that would collide with another derived metric,
 * refuses the part edit instead of leaving a stale composition behind.
 *
 * <p>Codes are unique per kind inside a Workspace, so a rename cannot collide with a sibling part;
 * the collision check only defends against rows written outside the application. The component owns
 * no state and joins the caller's transaction.
 */
@Service
public class MetricCompositionSync {
    /** The three part kinds a derived metric is composed from. */
    public enum Part {
        /** Atomic metric: the last code segment and the last name segment. */
        ATOMIC,
        /** Time period: the first code segment and the first name segment. */
        PERIOD,
        /** Modifier: one middle segment, positioned by its stored ordinal. */
        MODIFIER
    }

    @Resource
    private MetricDerivedRepository derived;

    /**
     * Recomposes every derived metric that uses an edited part.
     * @param workspaceId authorized current Workspace
     * @param actor authenticated editor recorded on the rewritten rows
     * @param part edited kind of part
     * @param partId edited part identity
     * @param code committed part code after the edit
     * @param name committed part name after the edit
     */
    public void partEdited(String workspaceId, String actor, Part part, String partId, String code, String name) {
        var affected =
                switch (part) {
                    case ATOMIC -> derived.byAtomic(workspaceId, partId);
                    case PERIOD -> derived.byPeriod(workspaceId, partId);
                    case MODIFIER -> derived.byModifier(workspaceId, partId);
                };
        for (var row : affected) {
            // The part lists carry no modifier links, so each row is read back in full; a row
            // deleted concurrently simply has nothing left to rewrite.
            var full = derived.find(workspaceId, row.getId()).orElse(null);
            if (full == null) continue;
            var composed = compose(full, part, partId, code, name);
            if (!DerivedComposition.fits(composed))
                throw new BusinessException(
                        MetricErrorCode.INVALID_DERIVED_METRIC,
                        "composed code of " + full.getEnCode() + " would exceed the column limit");
            if (derived.occupied(workspaceId, composed.enCode(), full.getId()))
                throw new BusinessException(
                        MetricErrorCode.DUPLICATE_CODE, "composed code " + composed.enCode() + " already exists");
            full.setEnCode(composed.enCode());
            full.setCnName(composed.cnName());
            full.initUpdate(actor);
            if (!derived.rename(full)) throw MetricCatalog.conflict();
        }
    }

    private static DerivedComposition.Composed compose(
            MetricDerivedEntity row, Part part, String partId, String code, String name) {
        List<String> modifierCodes = new ArrayList<>();
        List<String> modifierNames = new ArrayList<>();
        for (var link : row.getModifiers()) {
            boolean edited = part == Part.MODIFIER && partId.equals(link.getModifierId());
            modifierCodes.add(edited ? code : link.getModifierEnCode());
            modifierNames.add(edited ? name : link.getModifierCnName());
        }
        return DerivedComposition.compose(
                part == Part.PERIOD ? code : row.getPeriodEnCode(),
                modifierCodes,
                part == Part.ATOMIC ? code : row.getAtomicEnCode(),
                part == Part.PERIOD ? name : row.getPeriodCnName(),
                modifierNames,
                part == Part.ATOMIC ? name : row.getAtomicCnName());
    }
}
