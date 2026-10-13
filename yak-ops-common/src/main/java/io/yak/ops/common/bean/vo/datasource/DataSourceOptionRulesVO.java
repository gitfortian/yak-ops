package io.yak.ops.common.bean.vo.datasource;

import java.util.List;
import java.util.Objects;

/** Safe configuration metadata of one registered Datasource provider. */
public record DataSourceOptionRulesVO(String dbType, List<OptionDefinitionVO> options) {

    public DataSourceOptionRulesVO {
        Objects.requireNonNull(dbType, "dbType");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
    }

    /** Public option descriptor; configured values and credentials are not included. */
    public record OptionDefinitionVO(
            String key,
            String type,
            boolean required,
            Object defaultValue,
            List<String> allowedValues,
            String description) {

        public OptionDefinitionVO {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(type, "type");
            allowedValues = List.copyOf(Objects.requireNonNull(allowedValues, "allowedValues"));
            Objects.requireNonNull(description, "description");
        }
    }
}
