package io.yak.ops.business.datasource.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks datasource connection credentials and preserves unchanged secrets during editing.
 *
 * <p>Supported connection configurations contain fields, JDBC properties and nested SSH settings.
 * Plugin metadata declares additional credential fields when required.
 */
public final class DataSourceCredentialUtils {

    private static final Set<String> SECRET_FIELDS = Set.of(
            "password",
            "pwd",
            "secret",
            "secretkey",
            "secretaccesskey",
            "accesskey",
            "accesstoken",
            "token",
            "privatekey",
            "privatekeycontent",
            "passphrase",
            "privatekeypassphrase",
            "clientsecret");

    private static final Pattern ASSIGNMENT = Pattern.compile("(^|[?&;(\\s])([A-Za-z0-9_.%+-]+)=([^&;\\s()]+)");

    private DataSourceCredentialUtils() {}

    /**
     * Masks credentials in a saved connection configuration before returning it to the editor.
     *
     * @param descriptor metadata declaring provider-specific credential fields
     * @param connectionJson saved connection configuration
     * @return JSON with sensitive values masked, or {@code null} for blank input
     */
    public static String maskForDisplay(DataSourcePluginDescriptor descriptor, String connectionJson) {
        if (StringUtils.isBlank(connectionJson)) {
            return null;
        }
        ObjectNode connection = readObject(connectionJson);
        maskObject(connection, secretFields(descriptor));
        return JSONUtils.toJson(connection);
    }

    /**
     * Retains saved credentials when the editor submits blank or masked credential values.
     *
     * <p>Submitted non-secret fields and explicit replacement credentials take precedence.
     *
     * @param descriptor metadata declaring provider-specific credential fields
     * @param submittedJson edited connection configuration
     * @param storedJson saved connection configuration containing current credentials
     * @return edited JSON with unchanged credentials restored from the saved configuration
     */
    public static String retainOnUpdate(DataSourcePluginDescriptor descriptor, String submittedJson, String storedJson) {
        ObjectNode submitted = readObject(submittedJson);
        ObjectNode stored = readObject(storedJson);
        retainObject(submitted, stored, secretFields(descriptor));
        return JSONUtils.toJson(submitted);
    }

    /**
     * Masks recognized credential assignments and user information in a JDBC connection string.
     *
     * @param descriptor metadata declaring provider-specific credential fields
     * @param value JDBC URL or connection property text
     * @return text with recognized sensitive values masked, or the original blank value
     */
    public static String maskText(DataSourcePluginDescriptor descriptor, String value) {
        return maskAssignments(value, secretFields(descriptor));
    }

    private static void maskObject(ObjectNode object, Set<String> keys) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (isSecret(field.getKey(), keys)) {
                object.put(field.getKey(), SensitiveUtils.MASKED_VALUE);
            } else if (value.isObject()) {
                maskObject((ObjectNode) value, keys);
            } else if (value.isTextual()) {
                object.put(field.getKey(), maskAssignments(value.asText(), keys));
            } else if (value.isArray()) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS);
            }
        }
    }

    private static void retainObject(ObjectNode submitted, ObjectNode stored, Set<String> keys) {
        Iterator<Map.Entry<String, JsonNode>> fields = stored.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode previous = field.getValue();
            JsonNode current = submitted.get(fieldName);

            if (isSecret(fieldName, keys) && isUnchanged(current)) {
                submitted.set(fieldName, previous.deepCopy());
            } else if (previous.isObject() && current != null && current.isObject()) {
                retainObject((ObjectNode) current, (ObjectNode) previous, keys);
            } else if (previous.isTextual() && current != null && current.isTextual()) {
                submitted.put(fieldName, retainAssignments(current.asText(), previous.asText(), keys));
            }
        }
    }

    private static boolean isUnchanged(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (!value.isTextual()) {
            return false;
        }
        String text = value.asText().trim();
        return text.isEmpty() || SensitiveUtils.MASKED_VALUE.equals(text);
    }

    /**
     * Restores only masked sensitive assignments, leaving unrelated submitted parameters unchanged.
     */
    private static String retainAssignments(String submitted, String stored, Set<String> keys) {
        if (!submitted.contains(SensitiveUtils.MASKED_VALUE)) {
            return submitted;
        }
        Matcher edited = ASSIGNMENT.matcher(submitted);
        StringBuffer result = new StringBuffer();
        while (edited.find()) {
            if (!isSecret(edited.group(2), keys) || !SensitiveUtils.MASKED_VALUE.equals(edited.group(3))) {
                continue;
            }
            String fieldName = credentialKey(edited.group(2));
            String previousValue = null;
            Matcher previous = ASSIGNMENT.matcher(stored);
            while (previous.find()) {
                if (fieldName.equals(credentialKey(previous.group(2)))) {
                    if (previousValue != null) {
                        throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS);
                    }
                    previousValue = previous.group(3);
                }
            }
            if (previousValue == null) {
                throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS);
            }
            String replacement = edited.group(1) + edited.group(2) + "=" + previousValue;
            edited.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        edited.appendTail(result);
        return result.toString();
    }

    private static String maskAssignments(String value, Set<String> keys) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = ASSIGNMENT.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            if (isSecret(matcher.group(2), keys)) {
                String replacement = matcher.group(1) + matcher.group(2) + "=" + SensitiveUtils.MASKED_VALUE;
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);
        return SensitiveUtils.mask(result.toString());
    }

    private static boolean isSecret(String name, Set<String> keys) {
        String key = credentialKey(name);
        return keys.contains(key)
                || key.endsWith("password")
                || key.endsWith("secret")
                || key.endsWith("token")
                || key.endsWith("privatekey")
                || key.endsWith("passphrase")
                || key.endsWith("accesskey");
    }

    private static Set<String> secretFields(DataSourcePluginDescriptor descriptor) {
        if (descriptor == null || descriptor.secretFieldKeys().isEmpty()) {
            return SECRET_FIELDS;
        }
        Set<String> keys = new HashSet<>(SECRET_FIELDS);
        for (String name : descriptor.secretFieldKeys()) {
            keys.add(credentialKey(name));
        }
        return keys;
    }

    private static String credentialKey(String key) {
        String name = key;
        try {
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            // A malformed encoded name is still checked using its literal characters.
        }
        return name.trim()
                .replace("_", "")
                .replace("-", "")
                .replace(".", "")
                .toLowerCase(Locale.ROOT);
    }

    private static ObjectNode readObject(String value) {
        JsonNode node;
        try {
            node = JSONUtils.readTree(value);
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, null, exception);
        }
        if (node instanceof ObjectNode object) {
            return object;
        }
        throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS);
    }
}
