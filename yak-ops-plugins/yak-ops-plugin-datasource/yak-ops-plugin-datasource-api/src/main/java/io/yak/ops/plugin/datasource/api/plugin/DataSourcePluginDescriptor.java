package io.yak.ops.plugin.datasource.api.plugin;

import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.datasource.api.enums.DataSourceCapability;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Describes stable runtime capabilities of a datasource provider.
 *
 * <p>The provider owns its canonical type, aliases, capabilities and secret fields; the platform does not duplicate database type enums or UI form schemas.
 *
 * @param type canonical provider type
 * @param aliases supported aliases of the canonical type
 * @param apiVersion supported Datasource Plugin API version
 * @param capabilities capabilities currently implemented by the provider
 * @param secretFieldKeys provider-specific sensitive connection property names
 */
public record DataSourcePluginDescriptor(
        String type,
        Set<String> aliases,
        String apiVersion,
        Set<DataSourceCapability> capabilities,
        Set<String> secretFieldKeys) {

    /** Datasource Plugin API version accepted by this runtime. */
    public static final String CURRENT_API_VERSION = "5";

    public DataSourcePluginDescriptor {
        type = canonicalTypeName(type);
        aliases = immutableAliases(type, aliases);
        apiVersion = defaultIfBlank(apiVersion, CURRENT_API_VERSION);
        capabilities = immutableCapabilities(capabilities);
        secretFieldKeys = immutableSecretFieldKeys(secretFieldKeys);
    }

    public boolean matchesType(String value) {
        String normalized = canonicalTypeName(value);
        return type.equals(normalized) || aliases.contains(normalized);
    }

    public boolean supports(DataSourceCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public static String canonicalTypeName(String value) {
        String normalized = StringUtils.trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException("plugin type must not be blank");
        return normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static Set<String> immutableAliases(String type, Set<String> values) {
        if (CollectionUtils.isEmpty(values)) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String alias = canonicalTypeName(value);
            if (!type.equals(alias)) normalized.add(alias);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<DataSourceCapability> immutableCapabilities(Set<DataSourceCapability> values) {
        if (CollectionUtils.isEmpty(values)) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static Set<String> immutableSecretFieldKeys(Set<String> values) {
        if (CollectionUtils.isEmpty(values)) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String key = StringUtils.trimToNull(value);
            if (key != null) normalized.add(key);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String defaultIfBlank(String value, String fallback) {
        String normalized = StringUtils.trimToNull(value);
        return normalized == null ? fallback : normalized;
    }
}
