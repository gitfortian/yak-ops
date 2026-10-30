package io.yak.ops.common.enums.modeling;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Publication environment a layer target is bound to.
 *
 * <p>A layer binds one physical target per environment, so publishing asks which environment to
 * land in instead of asking for a database every time. {@code TEST} is deliberately absent: the
 * layer datasource itself already carries a test environment marker, and no caller asked for a
 * third publication lane.
 */
@Getter
@RequiredArgsConstructor
public enum PublicationEnvironment implements ErrorCode {
    /** Development target; typically the datasource whose own environment is DEVELOP. */
    DEVELOP(19301, "开发"),
    /** Production target; typically the datasource whose own environment is PROD. */
    PROD(19302, "生产");

    private final Integer code;
    private final String message;

    /**
     * Resolves an environment from its stored or submitted name.
     * @param value raw text
     * @return matching environment, or null when the value is not one this build knows
     */
    public static PublicationEnvironment from(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
