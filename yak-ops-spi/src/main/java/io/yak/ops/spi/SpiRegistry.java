package io.yak.ops.spi;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

/**
 * Discovers and indexes SPI providers by their declared identifiers.
 *
 * <p>Registration rejects missing and duplicate identifiers. The resulting index is immutable
 * and can be shared between callers without synchronization.
 *
 * @param <T> provider type
 */
public final class SpiRegistry<T> {

    private final Map<String, T> providers;

    private SpiRegistry(Map<String, T> providers) {
        this.providers = Collections.unmodifiableMap(providers);
    }

    /**
     * Discovers providers using Java SPI and registers their identifiers.
     *
     * @param spiClass service interface implemented by providers
     * @param identifiers extracts one or more registered identifiers from a provider
     * @param <T> provider type
     * @return immutable registry of discovered providers
     * @throws IllegalStateException if a provider identifier is missing or duplicated
     */
    public static <T> SpiRegistry<T> load(
            Class<T> spiClass, Function<? super T, ? extends Collection<String>> identifiers) {
        Objects.requireNonNull(spiClass, "spiClass");
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = spiClass.getClassLoader();
        }
        return of(ServiceLoader.load(spiClass, loader), identifiers);
    }

    /**
     * Registers existing provider instances without performing SPI discovery.
     *
     * @param candidates provider instances
     * @param identifiers extracts one or more registered identifiers from a provider
     * @param <T> provider type
     * @return immutable registry of the supplied providers
     * @throws IllegalStateException if a provider identifier is missing or duplicated
     */
    public static <T> SpiRegistry<T> of(
            Iterable<? extends T> candidates, Function<? super T, ? extends Collection<String>> identifiers) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(identifiers, "identifiers");
        Map<String, T> registered = new LinkedHashMap<>();
        for (T provider : candidates) {
            if (provider == null) {
                throw new IllegalStateException("SPI provider must not be null");
            }
            Collection<String> names = identifiers.apply(provider);
            if (names == null || names.isEmpty()) {
                throw new IllegalStateException("SPI provider must declare an identifier");
            }
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    throw new IllegalStateException("SPI provider identifier must not be blank");
                }
                if (registered.putIfAbsent(name, provider) != null) {
                    throw new IllegalStateException("Duplicate SPI provider identifier: " + name);
                }
            }
        }
        return new SpiRegistry<>(registered);
    }

    /**
     * Returns a provider registered under the exact identifier.
     *
     * @param identifier registered provider identifier
     * @return matching provider
     * @throws NoSuchElementException if no provider is registered under the identifier
     */
    public T get(String identifier) {
        T provider = providers.get(identifier);
        if (provider == null) {
            throw new NoSuchElementException("SPI provider not registered: " + identifier);
        }
        return provider;
    }

    /**
     * Returns the registered identifiers in registration order.
     *
     * @return immutable set of registered identifiers
     */
    public Set<String> types() {
        return providers.keySet();
    }
}
