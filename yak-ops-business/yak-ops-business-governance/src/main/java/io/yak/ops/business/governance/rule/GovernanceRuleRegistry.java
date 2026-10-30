package io.yak.ops.business.governance.rule;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * In-memory registry populated from Spring beans.
 *
 * <p>Startup fails fast when two beans declare the same code, a code is blank or a version is below
 * one. There is no {@code gov_rule} table and no startup upsert.
 */
@Component
public class GovernanceRuleRegistry {

    private final Map<String, GovernanceRule> rules = new LinkedHashMap<>();

    public GovernanceRuleRegistry(List<GovernanceRule> beans) {
        for (GovernanceRule rule : beans) {
            if (rule.code() == null || rule.code().isBlank()) {
                throw new IllegalStateException("A governance rule declares a blank code");
            }
            if (rule.version() < 1) {
                throw new IllegalStateException("Governance rule " + rule.code() + " declares version below 1");
            }
            if (rules.putIfAbsent(rule.code(), rule) != null) {
                throw new IllegalStateException("Duplicate governance rule code " + rule.code());
            }
        }
    }

    /** Registered rules in bean order; the list is immutable. */
    public List<GovernanceRule> rules() {
        return List.copyOf(rules.values());
    }

    /** Soft lookup for a read path that must survive a rule removed while its Issues remain. */
    public java.util.Optional<GovernanceRule> find(String code) {
        return java.util.Optional.ofNullable(rules.get(code));
    }

    public GovernanceRule require(String code) {
        GovernanceRule rule = rules.get(code);
        if (rule == null) {
            throw new IllegalStateException("Unknown governance rule " + code);
        }
        return rule;
    }
}
