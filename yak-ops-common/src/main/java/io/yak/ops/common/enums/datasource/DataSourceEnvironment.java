package io.yak.ops.common.enums.datasource;

import com.baomidou.mybatisplus.annotation.EnumValue;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Classifies the operating environment assigned to a datasource.
 */
@Getter
@RequiredArgsConstructor
public enum DataSourceEnvironment {
    DEVELOP(0, "开发"),
    TEST(1, "测试"),
    PROD(2, "生产");

    @EnumValue
    private final Integer value;

    private final String displayName;

    public static DataSourceEnvironment parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEVELOP;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("DEV".equals(normalized) || "DEVELOPMENT".equals(normalized)) {
            normalized = "DEVELOP";
        }
        if ("PRODUCTION".equals(normalized)) {
            normalized = "PROD";
        }

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的运行环境：" + value, exception);
        }
    }
}
