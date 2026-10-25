package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.connector.jdbc.sink.JdbcWriteMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sink-owned V3 target identity, write protocol and physical Connector projection. */
public record SinkConfig(ReadonlyConfig options, List<String> targetPrimaryKeys) {

    private static final Option<List<String>> PRIMARY_KEYS =
            Options.key("primaryKeys").listType(String.class).noDefaultValue();

    public static final OptionRule TABLE_RULE = OptionRule.builder()
            .required(
                    JobOptions.TABLE,
                    Conditions.notBlank(JobOptions.TABLE).and(Conditions.matches(JobOptions.TABLE, "(?s)^.{1,128}$")))
            .optional(JobOptions.DATABASE, Conditions.matches(JobOptions.DATABASE, "(?s)^.{0,128}$"))
            .optional(JobOptions.SCHEMA, Conditions.matches(JobOptions.SCHEMA, "(?s)^.{0,128}$"))
            .optional(PRIMARY_KEYS)
            .build();

    public static SinkConfig from(
            ReadonlyConfig target,
            String datasourceId,
            ReadonlyConfig mapping,
            SourceConfig source,
            boolean schemaChangesEnabled,
            boolean offlineIncremental) {
        ConfigValidator.of(target).validate(TABLE_RULE);
        ConfigValidator.validateUnknownKeys(target, TABLE_RULE, "tableMappings.target");
        List<String> keys = target.getSourceMap().containsKey(PRIMARY_KEYS.key()) ? target.get(PRIMARY_KEYS) : null;
        if (keys != null && schemaChangesEnabled) {
            throw new IllegalArgumentException("Runtime schema evolution is unsupported with custom target keys");
        }
        if (keys != null
                && (keys.isEmpty()
                        || keys.stream().anyMatch(key -> key == null || key.isBlank())
                        || new HashSet<>(keys).size() != keys.size())) {
            throw new IllegalArgumentException("Target primary key columns must be unique and nonempty");
        }
        JdbcWriteMode mode = mapping.get(JdbcSinkOptions.WRITE_MODE);
        if (source.keyless()) {
            if (mode != JdbcWriteMode.RETRACT && mode != JdbcWriteMode.UPSERT) {
                throw new IllegalArgumentException("Keyless CDC requires UPSERT or RETRACT");
            }
            if (mode == JdbcWriteMode.RETRACT && (!Boolean.TRUE.equals(source.transactionMarker()) || keys != null)) {
                throw new IllegalArgumentException("Keyless RETRACT requires XA and no target primary key");
            }
            if (mode == JdbcWriteMode.UPSERT && (source.transactionMarker() != null || keys == null)) {
                throw new IllegalArgumentException("Keyless UPSERT requires a verified target primary key without XA");
            }
        } else if (mode == JdbcWriteMode.RETRACT) {
            throw new IllegalArgumentException("RETRACT, XA and Chunk Key require explicit keyless CDC");
        }
        if (offlineIncremental && mode != JdbcWriteMode.UPSERT) {
            throw new IllegalArgumentException("Offline incremental requires UPSERT");
        }

        Map<String, Object> physical = new LinkedHashMap<>(target.getSourceMap());
        physical.remove(PRIMARY_KEYS.key());
        physical.put(JobOptions.DATASOURCE_ID.key(), datasourceId);
        physical.put(JobOptions.TYPE.key(), "JDBC");
        physical.put(JdbcSinkOptions.WRITE_MODE.key(), mode.name());
        if (source.keyless() && mode == JdbcWriteMode.RETRACT) {
            physical.put(JdbcSinkOptions.XA_ENABLED.key(), true);
        }
        return new SinkConfig(ReadonlyConfig.fromMap(physical), keys);
    }

    public SinkConfig {
        if (targetPrimaryKeys != null) targetPrimaryKeys = List.copyOf(targetPrimaryKeys);
    }
}
