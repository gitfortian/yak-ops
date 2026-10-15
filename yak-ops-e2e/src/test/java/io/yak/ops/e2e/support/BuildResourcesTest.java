package io.yak.ops.e2e.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Verifies Maven resource filtering without launching a database or application. */
class BuildResourcesTest {
    @Test
    void buildPathsAreExpandedBeforeLaunchingBoot() throws Exception {
        Properties build = new Properties();
        try (var input = getClass().getResourceAsStream("/backend-build.properties")) {
            assertNotNull(input, "Missing filtered build resource");
            build.load(input);
        }
        for (String key : new String[] {"yak.e2e.jar", "yak.e2e.jdbc-driver-dir"}) {
            String value = build.getProperty(key);
            assertNotNull(value, key);
            assertFalse(value.contains("${"), "Unresolved Maven expression: " + key);
            assertFalse(value.contains("@maven."), "Unresolved Maven delimiter: " + key);
            assertFalse(value.contains("@project."), "Unresolved project delimiter: " + key);
            assertTrue(Path.of(value).isAbsolute(), "Build path must be absolute: " + key);
        }
        assertTrue(build.getProperty("yak.e2e.jar").endsWith(".jar"));
    }
}
