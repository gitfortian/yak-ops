package io.yak.ops.connector.base.factory;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/**
 * Discovers Connector factories through Java SPI and validates their declared options.
 *
 * <p>Registered factories are stateless. A factory identifier is unique within one Source
 * or Sink kind, and is matched exactly to persisted configuration.
 */
public final class FactoryUtil {

    private final Map<String, TableSourceFactory> sources;
    private final Map<String, TableSinkFactory> sinks;

    /** Discovers all registered sources and sinks on the application classpath. */
    public FactoryUtil() {
        this(ServiceLoader.load(TableSourceFactory.class), ServiceLoader.load(TableSinkFactory.class));
    }

    /** Registers explicit providers for deterministic tests and embedded applications. */
    public FactoryUtil(
            Iterable<? extends TableSourceFactory> sources,
            Iterable<? extends TableSinkFactory> sinks) {
        this.sources = sourceFactories(sources);
        this.sinks = sinkFactories(sinks);
    }

    public TableSourceFactory source(String identifier) {
        TableSourceFactory factory = sources.get(identifier);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown Source factory identifier");
        }
        return factory;
    }

    public TableSinkFactory sink(String identifier) {
        TableSinkFactory factory = sinks.get(identifier);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown Sink factory identifier");
        }
        return factory;
    }

    /**
     * Validates declared Connector options together with a caller's shared endpoint options.
     *
     * <p>Unknown keys are rejected against the combined rule so a Connector cannot silently
     * consume undeclared task-definition fields.
     */
    public static void validate(ReadonlyConfig config, Factory factory, OptionRule common) {
        OptionRule rule = OptionRule.builder()
                .include(Objects.requireNonNull(common, "common"))
                .include(Objects.requireNonNull(factory, "factory").optionRule())
                .build();
        ConfigValidator.of(config).validate(rule);
        ConfigValidator.validateUnknownKeys(config, rule, factory.factoryIdentifier());
    }

    private static Map<String, TableSourceFactory> sourceFactories(Iterable<? extends TableSourceFactory> factories) {
        Map<String, TableSourceFactory> result = new LinkedHashMap<>();
        for (TableSourceFactory factory : factories) {
            register(result, factory);
        }
        return Map.copyOf(result);
    }

    private static Map<String, TableSinkFactory> sinkFactories(Iterable<? extends TableSinkFactory> factories) {
        Map<String, TableSinkFactory> result = new LinkedHashMap<>();
        for (TableSinkFactory factory : factories) {
            register(result, factory);
        }
        return Map.copyOf(result);
    }

    private static <T extends Factory> void register(Map<String, T> factories, T factory) {
        Objects.requireNonNull(factory, "factory");
        String identifier = factory.factoryIdentifier();
        if (identifier == null || identifier.isBlank() || factory.optionRule() == null) {
            throw new IllegalStateException("Connector Factory must declare identifier and option rules");
        }
        if (factories.putIfAbsent(identifier, factory) != null) {
            throw new IllegalStateException("Duplicate Connector Factory identifier: " + identifier);
        }
    }
}
