package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.validation.ConfigValidationError;
import io.yak.ops.configuration.validation.ConfigValidationResult;
import io.yak.ops.configuration.validation.OptionValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluates declarative option rules without accessing external resources.
 *
 * <p>Unknown options are checked separately so components may compose common and plugin-specific
 * option rules before applying a strict allowlist.
 */
public final class ConfigValidator {

    private final ReadonlyConfig config;

    private ConfigValidator(ReadonlyConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public static ConfigValidator of(ReadonlyConfig config) {
        return new ConfigValidator(config);
    }

    public void validate(OptionRule rule) {
        ConfigValidationResult result = validateResult(rule);
        if (!result.valid()) {
            throw new OptionValidationException(result.errors());
        }
    }

    public ConfigValidationResult validateResult(OptionRule rule) {
        List<ConfigValidationError> errors = new ArrayList<>();
        collectErrors(Objects.requireNonNull(rule, "rule"), errors);
        return errors.isEmpty() ? ConfigValidationResult.success() : ConfigValidationResult.failure(errors);
    }

    private void collectErrors(OptionRule rule, List<ConfigValidationError> errors) {
        Set<String> missing = new HashSet<>();
        for (RequiredOption group : rule.getRequiredOptions()) {
            if (group.kind() == RequiredOption.Kind.CONDITIONAL && !group.when().evaluate(config)) {
                continue;
            }
            List<Option<?>> present =
                    group.options().stream().filter(config::contains).toList();
            boolean valid =
                    switch (group.kind()) {
                        case REQUIRED, CONDITIONAL ->
                            present.size() == group.options().size();
                        case EXCLUSIVE -> present.size() == 1;
                        case BUNDLED ->
                            present.isEmpty()
                                    || present.size() == group.options().size();
                    };
            if (!valid) {
                String groupPath = group.options().stream()
                        .map(Option::key)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
                errors.add(new ConfigValidationError(groupPath, group.kind().name(), "Option group constraint failed"));
                if (group.kind() == RequiredOption.Kind.REQUIRED || group.kind() == RequiredOption.Kind.CONDITIONAL) {
                    group.options().stream()
                            .filter(option -> !config.contains(option))
                            .map(Option::key)
                            .forEach(missing::add);
                }
            }
            for (Option<?> option : present) {
                if (!option.allowedValues().isEmpty() && !option.allowedValues().contains(config.get(option))) {
                    errors.add(new ConfigValidationError(option.key(), "SINGLE_CHOICE", "Invalid option choice"));
                }
            }
        }
        for (Option<?> option : rule.getOptionalOptions()) {
            if (config.contains(option)
                    && !option.allowedValues().isEmpty()
                    && !option.allowedValues().contains(config.get(option))) {
                errors.add(new ConfigValidationError(option.key(), "SINGLE_CHOICE", "Invalid option choice"));
            }
        }
        for (Condition<?> condition : rule.getValueConstraints()) {
            if (!condition.optionKeys().stream().allMatch(config::containsKey)
                    || condition.optionKeys().stream().anyMatch(missing::contains)) {
                continue;
            }
            try {
                if (!condition.evaluate(config)) {
                    errors.add(new ConfigValidationError(
                            String.join(",", condition.optionKeys()), "VALUE", condition.description()));
                }
            } catch (RuntimeException exception) {
                errors.add(new ConfigValidationError(
                        String.join(",", condition.optionKeys()), "VALUE", "Invalid option value"));
            }
        }
        for (ConditionRule conditionRule : rule.getConditionRules()) {
            if (conditionRule.when().evaluate(config)) {
                collectErrors(conditionRule.optionRule(), errors);
            }
        }
    }

    /** Rejects keys absent from the declared option set, including undeclared nested paths. */
    public static void validateUnknownKeys(ReadonlyConfig config, OptionRule rule, String component) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(rule, "rule");
        Set<String> declared = declaredKeys(rule);
        List<ConfigValidationError> errors = new ArrayList<>();
        checkPaths(config.getSourceMap(), "", declared, errors);
        if (!errors.isEmpty()) {
            throw new OptionValidationException(errors);
        }
    }

    private static void checkPaths(
            Map<String, ?> values, String prefix, Set<String> declared, List<ConfigValidationError> errors) {
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (declared.contains(path)) {
                continue;
            }
            boolean namespace = declared.stream().anyMatch(key -> key.startsWith(path + "."));
            if (!namespace) {
                errors.add(new ConfigValidationError(path, "UNKNOWN", "Unknown configuration option"));
            } else if (entry.getValue() instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, ?> valuesByName = (Map<String, ?>) nested;
                checkPaths(valuesByName, path, declared, errors);
            } else {
                errors.add(new ConfigValidationError(path, "TYPE", "Configuration namespace must be an object"));
            }
        }
    }

    private static Set<String> declaredKeys(OptionRule rule) {
        Set<String> keys = new LinkedHashSet<>();
        rule.getOptionalOptions().forEach(option -> keys.add(option.key()));
        for (RequiredOption group : rule.getRequiredOptions()) {
            group.options().forEach(option -> keys.add(option.key()));
            if (group.when() != null) {
                keys.addAll(group.when().condition().optionKeys());
            }
        }
        rule.getValueConstraints().forEach(condition -> keys.addAll(condition.optionKeys()));
        for (ConditionRule ruleGroup : rule.getConditionRules()) {
            keys.addAll(ruleGroup.when().condition().optionKeys());
            keys.addAll(declaredKeys(ruleGroup.optionRule()));
        }
        return keys;
    }
}
