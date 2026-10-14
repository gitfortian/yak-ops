package io.yak.ops.plugin.task.datasync.solution;

import io.yak.ops.connector.base.factory.FactoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Enumerates implemented product solutions without opening datasource connections. */
public final class SolutionCatalog {
    private static final Set<String> JDBC_TYPES = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");
    private final FactoryUtil factories;

    public SolutionCatalog(FactoryUtil factories) {
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    /**
     * Returns choices for an ordered pair of product datasource types.
     *
     * @param sourceType datasource type, not a connector identifier
     * @param targetType datasource type, not a connector identifier
     * @return immutable supported choices; no resource authorization is implied
     */
    public List<Solution> list(String sourceType, String targetType) {
        validateTypes(sourceType, targetType);
        factories.sink("JDBC");
        List<Solution> choices = new ArrayList<>();
        choices.add(solution(targetType, SolutionType.SINGLE_TABLE_OFFLINE));
        if ("MYSQL".equals(sourceType)) {
            choices.add(solution(targetType, SolutionType.SINGLE_TABLE_REALTIME));
        }
        choices.add(solution(targetType, SolutionType.DATABASE_OFFLINE));
        if ("MYSQL".equals(sourceType) && !"ORACLE".equals(targetType)) {
            choices.add(solution(targetType, SolutionType.DATABASE_REALTIME));
        }
        return List.copyOf(choices);
    }

    /**
     * Validates resolved datasource types for the selected solution without connecting.
     *
     * @param type selected product solution
     * @param sourceType resolved source datasource type
     * @param targetType resolved target datasource type
     */
    public static void validate(SolutionType type, String sourceType, String targetType) {
        validateTypes(sourceType, targetType);
        if (type == SolutionType.DATABASE_REALTIME && "ORACLE".equals(targetType)) {
            throw new IllegalArgumentException(
                    "Database realtime requires a target with a compatible BIGINT snapshot key");
        }
        if (type.steps().contains(Step.INCREMENTAL) && !"MYSQL".equals(sourceType)) {
            throw new IllegalArgumentException("Realtime synchronization requires a MySQL source");
        }
    }

    private static void validateTypes(String sourceType, String targetType) {
        if (sourceType == null
                || targetType == null
                || !JDBC_TYPES.contains(sourceType)
                || !JDBC_TYPES.contains(targetType)) {
            throw new IllegalArgumentException("Unknown synchronization datasource type");
        }
    }

    /**
     * Checks whether the selected target supports initial schema preparation.
     *
     * @param targetType resolved product datasource type
     * @param steps validated requested steps
     */
    public static void validateSteps(String targetType, List<Step> steps) {
        if (steps.contains(Step.SCHEMA) && !Set.of("MYSQL", "POSTGRE_SQL").contains(targetType)) {
            throw new IllegalArgumentException("Schema preparation requires a MySQL or PostgreSQL target");
        }
    }

    private Solution solution(String targetType, SolutionType type) {
        factories.source(type.sourceIdentifier());
        List<String> writes = List.of("UPSERT");
        if (type.supportsScheduling()) {
            writes = List.of("APPEND", "UPSERT");
        }
        List<Step> applicable = new ArrayList<>(type.steps());
        List<Solution.Combination> combinations = new ArrayList<>();
        combinations.add(new Solution.Combination(type.steps(), type.supportsScheduling(), writes));
        if (Set.of("MYSQL", "POSTGRE_SQL").contains(targetType)) {
            applicable.addFirst(Step.SCHEMA);
            combinations.add(new Solution.Combination(applicable, type.supportsScheduling(), writes));
        }
        return new Solution(type, type.displayName(), applicable, combinations, type.steps());
    }
}
