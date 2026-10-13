package io.yak.ops.plugin.task.datasync.connector;

import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import io.yak.ops.spi.SpiRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Discovers independent Source and Sink factories through the shared Java SPI registry.
 *
 * <p>Identifiers resolve saved JSON. Exact parameters types resolve compiled definitions;
 * ambiguous identifiers or parameter classes are rejected at registry construction.
 */
public final class SyncConnectorFactoryRegistry {

    private final SpiRegistry<SyncSourceFactory> sources;
    private final SpiRegistry<SyncSinkFactory> sinks;
    private final Map<Class<?>, SyncSourceFactory> sourcesByClass;
    private final Map<Class<?>, SyncSinkFactory> sinksByClass;

    /** Discovers all classpath providers through Java SPI. */
    public SyncConnectorFactoryRegistry() {
        this(
                SpiRegistry.load(SyncSourceFactory.class, factory -> java.util.List.of(factory.identifier())),
                SpiRegistry.load(SyncSinkFactory.class, factory -> java.util.List.of(factory.identifier())));
    }

    /** Registers explicit factories for embedding and tests. */
    public SyncConnectorFactoryRegistry(
            Iterable<? extends SyncSourceFactory> sourceFactories, Iterable<? extends SyncSinkFactory> sinkFactories) {
        this(
                SpiRegistry.of(sourceFactories, factory -> java.util.List.of(factory.identifier())),
                SpiRegistry.of(sinkFactories, factory -> java.util.List.of(factory.identifier())));
    }

    private SyncConnectorFactoryRegistry(SpiRegistry<SyncSourceFactory> sources, SpiRegistry<SyncSinkFactory> sinks) {
        this.sources = Objects.requireNonNull(sources, "sources");
        this.sinks = Objects.requireNonNull(sinks, "sinks");
        Map<Class<?>, SyncSourceFactory> registeredSources = new LinkedHashMap<>();
        for (String identifier : sources.types()) {
            SyncSourceFactory factory = sources.get(identifier);
            Class<? extends SourceParameters> type = Objects.requireNonNull(factory.parametersType(), "source type");
            if (registeredSources.putIfAbsent(type, factory) != null) {
                throw new IllegalStateException("Duplicate DATA_SYNC Source parameters class");
            }
        }
        Map<Class<?>, SyncSinkFactory> registeredSinks = new LinkedHashMap<>();
        for (String identifier : sinks.types()) {
            SyncSinkFactory factory = sinks.get(identifier);
            Class<? extends SinkParameters> type = Objects.requireNonNull(factory.parametersType(), "sink type");
            if (registeredSinks.putIfAbsent(type, factory) != null) {
                throw new IllegalStateException("Duplicate DATA_SYNC Sink parameters class");
            }
        }
        sourcesByClass = Map.copyOf(registeredSources);
        sinksByClass = Map.copyOf(registeredSinks);
    }

    /** Resolves a saved Source type identifier. */
    public SyncSourceFactory source(String identifier) {
        try {
            return sources.get(identifier);
        } catch (NoSuchElementException exception) {
            throw new TaskPluginException("Unknown DATA_SYNC Source type", exception);
        }
    }

    /** Resolves an already-decoded Source by its exact parameters class. */
    public SyncSourceFactory source(SourceParameters parameters) {
        SyncSourceFactory factory = sourcesByClass.get(Objects.requireNonNull(parameters, "parameters").getClass());
        if (factory == null) {
            throw new TaskPluginException("Unsupported DATA_SYNC Source parameters");
        }
        return factory;
    }

    /** Resolves a saved Sink type identifier. */
    public SyncSinkFactory sink(String identifier) {
        try {
            return sinks.get(identifier);
        } catch (NoSuchElementException exception) {
            throw new TaskPluginException("Unknown DATA_SYNC Sink type", exception);
        }
    }

    /** Resolves an already-decoded Sink by its exact parameters class. */
    public SyncSinkFactory sink(SinkParameters parameters) {
        SyncSinkFactory factory = sinksByClass.get(Objects.requireNonNull(parameters, "parameters").getClass());
        if (factory == null) {
            throw new TaskPluginException("Unsupported DATA_SYNC Sink parameters");
        }
        return factory;
    }
}
