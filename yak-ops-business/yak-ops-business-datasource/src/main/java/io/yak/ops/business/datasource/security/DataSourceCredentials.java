package io.yak.ops.business.datasource.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.SensitiveUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginDescriptor;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Protects datasource credentials in responses and retains unchanged secrets during editing.
 *
 * <p>Only matching credential fields are restored. Submitted non-secret fields remain authoritative.
 */
@Component
public class DataSourceCredentials {

    private static final Set<String> COMMON_SECRET_FIELDS = Set.of(
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

    private static final Pattern ASSIGNMENT = Pattern.compile("(^|[?&;(\\s])([A-Za-z0-9_.%+-]+)(=)([^&;\\s()]+)");

    /** Masks credential fields and embedded JDBC URL parameters in the editable connection JSON. */
    public String maskForDisplay(DataSourcePluginDescriptor descriptor, String connectionJson) {
        if (StringUtils.isBlank(connectionJson)) {
            return null;
        }
        ObjectNode connection = readObject(connectionJson);
        Set<String> keys = secretFields(descriptor);
        maskObject(connection, keys);
        return JSONUtils.toJson(connection);
    }

    /** Preserves stored credentials when an edit submits an absent, blank or masked credential field. */
    public String retainOnUpdate(DataSourcePluginDescriptor descriptor, String submittedJson, String storedJson) {
        ObjectNode submitted = readObject(submittedJson);
        ObjectNode stored = readObject(storedJson);
        retainSecrets(submitted, stored, secretFields(descriptor));
        return JSONUtils.toJson(submitted);
    }

    /** Masks credential-bearing parameters in JDBC URLs and provider diagnostic text. */
    public String maskText(DataSourcePluginDescriptor descriptor, String value) {
        return maskText(value, secretFields(descriptor));
    }

    private void maskObject(ObjectNode object, Set<String> keys) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode value = field.getValue();
            if (isSecret(field.getKey(), keys)) {
                object.put(field.getKey(), SensitiveUtils.MASKED_VALUE);
            } else if (value.isObject()) {
                maskObject((ObjectNode) value, keys);
            } else if (value.isArray()) {
                maskArray((ArrayNode) value, keys);
            } else if (value.isTextual()) {
                object.put(field.getKey(), maskText(value.asText(), keys));
            }
        }
    }

    private void maskArray(ArrayNode array, Set<String> keys) {
        for (int index = 0; index < array.size(); index++) {
            JsonNode value = array.get(index);
            if (value.isObject()) {
                maskObject((ObjectNode) value, keys);
            } else if (value.isArray()) {
                maskArray((ArrayNode) value, keys);
            } else if (value.isTextual()) {
                array.set(index, TextNode.valueOf(maskText(value.asText(), keys)));
            }
        }
    }

    private void retainSecrets(ObjectNode submitted, ObjectNode stored, Set<String> keys) {
        Iterator<Map.Entry<String, JsonNode>> fields = stored.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode previous = field.getValue();
            JsonNode current = submitted.get(name);

            if (isSecret(name, keys) && isUnchanged(current)) {
                submitted.set(name, previous.deepCopy());
            } else if (previous.isObject() && current != null && current.isObject()) {
                retainSecrets((ObjectNode) current, (ObjectNode) previous, keys);
            } else if (("jdbcUrl".equals(name) || "url".equals(name))
                    && previous.isTextual()
                    && current != null
                    && current.isTextual()) {
                submitted.put(name, retainUrlSecrets(current.asText(), previous.asText(), keys));
            }
        }
    }

    private String retainUrlSecrets(String submitted, String stored, Set<String> keys) {
        if (!submitted.contains(SensitiveUtils.MASKED_VALUE)) {
            return submitted;
        }

        Map<String, String> storedSecrets = new LinkedHashMap<>();
        Matcher storedParameters = ASSIGNMENT.matcher(stored);
        while (storedParameters.find()) {
            String name = normalizeField(decodeField(storedParameters.group(2)));
            if (isSecret(name, keys)) {
                String previous = storedSecrets.putIfAbsent(name, storedParameters.group(4));
                if (previous != null) {
                    throw new DataSourceException(
                            DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接地址中的敏感参数重复，请重新输入凭据");
                }
            }
        }

        Matcher submittedParameters = ASSIGNMENT.matcher(submitted);
        StringBuffer result = new StringBuffer();
        while (submittedParameters.find()) {
            String name = normalizeField(decodeField(submittedParameters.group(2)));
            String replacement = submittedParameters.group();
            if (isSecret(name, keys) && SensitiveUtils.MASKED_VALUE.equals(submittedParameters.group(4))) {
                String previous = storedSecrets.get(name);
                if (previous == null) {
                    throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接地址中的凭据需要重新输入");
                }
                replacement = submittedParameters.group(1) + submittedParameters.group(2) + "=" + previous;
            }
            submittedParameters.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        submittedParameters.appendTail(result);
        if (result.indexOf(SensitiveUtils.MASKED_VALUE) >= 0) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接地址中的凭据需要重新输入");
        }
        return result.toString();
    }

    private boolean isUnchanged(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (!value.isTextual()) {
            return false;
        }
        String text = value.asText().trim();
        return text.isEmpty() || SensitiveUtils.MASKED_VALUE.equals(text);
    }

    private String maskText(String value, Set<String> keys) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        Matcher matcher = ASSIGNMENT.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String fieldName = decodeField(matcher.group(2));
            String replacement = matcher.group();
            if (isSecret(fieldName, keys)) {
                replacement = matcher.group(1) + matcher.group(2) + "=" + SensitiveUtils.MASKED_VALUE;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return SensitiveUtils.mask(result.toString());
    }

    private String decodeField(String field) {
        try {
            return URLDecoder.decode(field, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return field;
        }
    }

    private boolean isSecret(String field, Set<String> keys) {
        String name = normalizeField(field);
        return COMMON_SECRET_FIELDS.contains(name)
                || keys.contains(name)
                || name.endsWith("password")
                || name.endsWith("secret")
                || name.endsWith("token")
                || name.endsWith("privatekey")
                || name.endsWith("passphrase")
                || name.endsWith("accesskey");
    }

    private Set<String> secretFields(DataSourcePluginDescriptor descriptor) {
        Set<String> keys = new LinkedHashSet<>();
        if (descriptor != null) {
            for (String name : descriptor.secretFieldKeys()) {
                keys.add(normalizeField(name));
            }
        }
        return keys;
    }

    private String normalizeField(String field) {
        return field == null
                ? ""
                : field.trim()
                        .replace("_", "")
                        .replace("-", "")
                        .replace(".", "")
                        .toLowerCase(Locale.ROOT);
    }

    private ObjectNode readObject(String json) {
        try {
            JsonNode value = JSONUtils.readTree(json);
            if (value instanceof ObjectNode object) {
                return object;
            }
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接参数必须是 JSON 对象");
        } catch (IllegalArgumentException exception) {
            throw new DataSourceException(DataSourceErrorCode.INVALID_CONNECTION_PARAMS, "连接参数不是合法 JSON", exception);
        }
    }
}
