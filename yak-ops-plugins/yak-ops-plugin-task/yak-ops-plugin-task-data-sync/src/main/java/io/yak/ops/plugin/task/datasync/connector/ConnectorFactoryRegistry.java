package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import io.yak.ops.spi.SpiRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Discovers independent Source and Sink factories through the shared Java SPI registry.
 *
 * <p>Identifiers resolve saved JSON. Exact parameters types resolve compiled definitions;
 * ambiguous identifiers or parameter classes are rejected at registry construction.
 */
public final class ConnectorFactoryRegistry {

    private final SpiRegistry<TableSourceFactory> sources;
    private final SpiRegistry<TableSinkFactory> sinks;
    private final Map<Class<?>, TableSourceFactory> sourcesByClass;
    private final Map<Class<?>, TableSinkFactory> sinksByClass;

    /** Discovers all classpath providers through Java SPI. */
    public ConnectorFactoryRegistry() {
        this(
                SpiRegistry.load(TableSourceFactory.class, factory -> List.of(factory.identifier())),
                SpiRegistry.load(TableSinkFactory.class, factory -> List.of(factory.identifier())));
    }

    /** Registers explicit factories for embedding and tests. */
    public ConnectorFactoryRegistry(
            Iterable<? extends TableSourceFactory> sourceFactories, Iterable<? extends TableSinkFactory> sinkFactories) {
        this(
                SpiRegistry.of(sourceFactories, factory -> List.of(factory.identifier())),
                SpiRegistry.of(sinkFactories, factory -> List.of(factory.identifier())));
    }

    private ConnectorFactoryRegistry(SpiRegistry<TableSourceFactory> sources, SpiRegistry<TableSinkFactory> sinks) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        Map<Class<?>, TableSourceFactory> registeredSources = new LinkedHashMap<>();
        for (String identifier : sources.types()) {
            TableSourceFactory factory = sources.get(identifier);
            Class<? extends SourceParameters> type = Objects.requireNonNull(factory.parametersType(), "source type");
            if (registeredSources.putIfAbsent(type, factory) != null) {
                throw new IllegalStateException("Duplicate DATA_SYNC Source parameters class");
            }
        }
        Map<Class<?>, TableSinkFactory> registeredSinks = new LinkedHashMap<>();
        for (String identifier : sinks.types()) {
            TableSinkFactory factory = sinks.get(identifier);
            Class<? extends SinkParameters> type = Objects.requireNonNull(factory.parametersType(), "sink type");
            if (registeredSinks.putIfAbsent(type, factory) != null) {
                throw new IllegalStateException("Duplicate DATA_SYNC Sink parameters class");
            }
        }
        sourcesByClass = Map.copyOf(registeredSources);
        sinksByClass = Map.copyOf(registeredSinks);
    }

    /** Resolves a saved Source type identifier. */
    public TableSourceFactory source(String identifier) {
        try {
            return sources.get(identifier);
        } catch (NoSuchElementException exception) {
            throw new TaskPluginException("Unknown DATA_SYNC Source type", exception);
        }
    }

    /** Resolves an already-decoded Source by its exact parameters class. */
    public TableSourceFactory source(SourceParameters parameters) {
        TableSourceFactory factory = sourcesByClass.get(
                Objects.requireNonNull(parameters, "parameters").getClass());
        if (factory == null || !factory.identifier().equals(parameters.type())) {
            throw new TaskPluginException("Unsupported DATA_SYNC Source parameters");
        }
        return factory;
    }

    /** Resolves a saved Sink type identifier. */
    public TableSinkFactory sink(String identifier) {
        try {
            return sinks.get(identifier);
        } catch (NoSuchElementException exception) {
            throw new TaskPluginException("Unknown DATA_SYNC Sink type", exception);
        }
    }

    /** Resolves an already-decoded Sink by its exact parameters class. */
    public TableSinkFactory sink(SinkParameters parameters) {
        TableSinkFactory factory = sinksByClass.get(
                Objects.requireNonNull(parameters, "parameters").getClass());
        if (factory == null || !factory.identifier().equals(parameters.type())) {
            throw new TaskPluginException("Unsupported DATA_SYNC Sink parameters");
        }
        return factory;
    }
}
