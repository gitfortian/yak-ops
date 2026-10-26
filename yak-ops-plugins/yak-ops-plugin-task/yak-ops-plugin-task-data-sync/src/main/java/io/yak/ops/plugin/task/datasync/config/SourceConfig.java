package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Source-owned V3 table selection and read options, projected into physical Connector settings. */
public record SourceConfig(
        ReadonlyConfig options, boolean keyless, Boolean transactionMarker, IncrementalConfig incremental) {

    private static final Option<Boolean> KEYLESS =
            Options.key("keyless").booleanType().defaultValue(false);
    private static final Option<String> CHUNK_KEY =
            Options.key("chunkKeyColumn").stringType().noDefaultValue();
    /** V3 transaction marker is read only for validating the persisted Sink protocol. */
    private static final Option<Boolean> XA = Options.key("xa").booleanType().noDefaultValue();

    public static final Option<Map<String, Object>> INCREMENTAL =
            Options.key("incremental").mapObjectType().noDefaultValue();

    public static final OptionRule TABLE_RULE = OptionRule.builder()
            .required(
                    JobOptions.TABLE,
                    Conditions.notBlank(JobOptions.TABLE).and(Conditions.matches(JobOptions.TABLE, "(?s)^.{1,128}$")))
            .optional(JobOptions.DATABASE, Conditions.matches(JobOptions.DATABASE, "(?s)^.{0,128}$"))
            .optional(JobOptions.SCHEMA, Conditions.matches(JobOptions.SCHEMA, "(?s)^.{0,128}$"))
            .optional(KEYLESS, CHUNK_KEY, XA)
            .build();

    public static SourceConfig from(
            ReadonlyConfig table,
            String datasourceId,
            SolutionType type,
            List<String> steps,
            boolean schemaChangesEnabled,
            ReadonlyConfig mapping) {
        ConfigValidator.of(table).validate(TABLE_RULE);
        ConfigValidator.validateUnknownKeys(table, TABLE_RULE, "tableMappings.source");
        Object rawKeyless = table.getSourceMap().get(KEYLESS.key());
        if (rawKeyless != null && !(rawKeyless instanceof Boolean)) {
            throw new IllegalArgumentException("keyless must be a boolean");
        }
        boolean keyless = table.get(KEYLESS);
        Boolean marker = table.getSourceMap().containsKey(XA.key()) ? table.get(XA) : null;
        String chunkKey = table.get(CHUNK_KEY);
        if (keyless) {
            if (type != SolutionType.SINGLE_TABLE_REALTIME
                    || !steps.containsAll(List.of(Step.FULL.name(), Step.INCREMENTAL.name()))
                    || schemaChangesEnabled
                    || chunkKey == null
                    || chunkKey.isBlank()) {
                throw new IllegalArgumentException("Keyless CDC requires single-table FULL + INCREMENTAL, "
                        + "an explicit Chunk Key and disabled runtime schema changes");
            }
        } else if (marker != null || table.getSourceMap().containsKey(CHUNK_KEY.key())) {
            throw new IllegalArgumentException("RETRACT, XA and Chunk Key require explicit keyless CDC");
        }

        boolean offlineIncremental = type.supportsScheduling() && steps.contains(Step.INCREMENTAL.name());
        IncrementalConfig incremental = null;
        if (offlineIncremental) {
            Map<String, Object> options = mapping.get(INCREMENTAL);
            if (options == null) throw new IllegalArgumentException("Incremental table settings are required");
            incremental = IncrementalConfig.from(options);
        } else if (mapping.getSourceMap().containsKey(INCREMENTAL.key())) {
            throw new IllegalArgumentException("Incremental settings require an offline incremental step");
        }

        Map<String, Object> physical = new LinkedHashMap<>(table.getSourceMap());
        physical.remove(KEYLESS.key());
        physical.remove(CHUNK_KEY.key());
        physical.remove(XA.key());
        physical.put(JobOptions.DATASOURCE_ID.key(), datasourceId);
        physical.put(JobOptions.TYPE.key(), type.sourceIdentifier());
        if (keyless) {
            physical.put(MySqlCdcSourceOptions.KEYLESS_ENABLED.key(), true);
            physical.put(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN.key(), chunkKey);
        }
        return new SourceConfig(ReadonlyConfig.fromMap(physical), keyless, marker, incremental);
    }
}
