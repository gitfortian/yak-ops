package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.jdbc.sink.JdbcSinkOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import java.util.List;
import java.util.Map;

/** Public version-three task options; physical connector settings remain engine-owned. */
public final class SolutionOptions {
    public static final Option<SolutionType> SOLUTION_TYPE =
            Options.key("solutionType").enumType(SolutionType.class).noDefaultValue();
    public static final Option<List<String>> STEPS =
            Options.key("steps").listType(String.class).noDefaultValue();
    public static final Option<Map<String, Object>> TABLE_SELECTION =
            Options.key("tableSelection").mapObjectType().noDefaultValue();
    public static final Option<String> SELECTION_MODE =
            Options.key("mode").stringType().noDefaultValue();
    public static final Option<List<Object>> TABLE_MAPPINGS =
            Options.key("tableMappings").listType(Object.class).noDefaultValue();
    /** Stored per Task Definition. Absent on existing definitions means evolution remains disabled. */
    public static final Option<Boolean> SCHEMA_CHANGES_ENABLED =
            Options.key("schemaChangesEnabled").booleanType().defaultValue(false);

    /** Explicit permission to remove managed non-key columns from the target database. */
    public static final Option<Boolean> ALLOW_DROP_COLUMNS =
            Options.key("allowDropColumns").booleanType().defaultValue(false);

    public static final OptionRule RULE = OptionRule.builder()
            .required(SOLUTION_TYPE, STEPS, JobOptions.SOURCE, JobOptions.SINK, TABLE_SELECTION, TABLE_MAPPINGS)
            .optional(JobOptions.TRANSFORM, SCHEMA_CHANGES_ENABLED, ALLOW_DROP_COLUMNS)
            .build();
    public static final OptionRule CONNECTION_RULE = OptionRule.builder()
            .required(
                    JobOptions.DATASOURCE_ID,
                    Conditions.notBlank(JobOptions.DATASOURCE_ID)
                            .and(Conditions.matches(JobOptions.DATASOURCE_ID, "(?s)^.{1,64}$")))
            .build();
    public static final OptionRule SELECTION_RULE =
            OptionRule.builder().required(SELECTION_MODE).build();
    public static final Option<Map<String, Object>> TARGET =
            Options.key("target").mapObjectType().noDefaultValue();
    public static final OptionRule TABLE_MAPPING_RULE = OptionRule.builder()
            .required(JobOptions.SOURCE, TARGET, JdbcSinkOptions.WRITE_MODE)
            .optional(SourceConfig.INCREMENTAL)
            .build();
    private SolutionOptions() {}
}
