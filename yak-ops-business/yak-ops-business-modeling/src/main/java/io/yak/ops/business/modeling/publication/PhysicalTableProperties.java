package io.yak.ops.business.modeling.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;

/**
 * Engine-specific physical table properties declared on a logical model.
 *
 * <p>The model stores a raw JSON object; this type parses it once and binds every field to the
 * dialect that can express it. Unknown keys are rejected instead of ignored, so a typo can never
 * silently drop a property the user believed they had set.
 *
 * @param dataModel Doris table model, {@code UNIQUE} or {@code DUPLICATE}; null otherwise
 * @param bucketCount Doris hash bucket count, 1..1024; null otherwise
 * @param replicaCount Doris replica number, 1..32; null otherwise
 */
public record PhysicalTableProperties(String dataModel, Integer bucketCount, Integer replicaCount) {

    /** The empty declaration every engine accepts. */
    public static final PhysicalTableProperties NONE = new PhysicalTableProperties(null, null, null);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_BUCKETS = 1024;
    private static final int MAX_REPLICAS = 32;

    /**
     * Parses and validates the stored JSON against one target dialect.
     *
     * @param raw stored JSON object, null or blank meaning none
     * @param dialect dialect the properties would be applied to
     * @return parsed properties, {@link #NONE} when nothing is declared
     * @throws IllegalArgumentException when the JSON is malformed, carries unknown keys, or
     *     declares a property the dialect cannot express
     */
    public static PhysicalTableProperties parse(String raw, PhysicalDialect dialect) {
        if (raw == null || raw.isBlank()) return NONE;
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("物理属性不是合法的 JSON 对象", malformed);
        }
        if (!root.isObject()) throw new IllegalArgumentException("物理属性必须是 JSON 对象");
        var dataModel = textField(root, "dataModel");
        var bucketCount = intField(root, "bucketCount");
        var replicaCount = intField(root, "replicaCount");
        for (Iterator<Map.Entry<String, JsonNode>> fields = root.fields(); fields.hasNext(); ) {
            var key = fields.next().getKey();
            if (!key.equals("dataModel") && !key.equals("bucketCount") && !key.equals("replicaCount")) {
                throw new IllegalArgumentException("物理属性包含不支持的键 " + key);
            }
        }
        var properties = new PhysicalTableProperties(dataModel, bucketCount, replicaCount);
        properties.validate(dialect);
        return properties;
    }

    /** Whether any property is declared. */
    public boolean isEmpty() {
        return dataModel == null && bucketCount == null && replicaCount == null;
    }

    private void validate(PhysicalDialect dialect) {
        if (isEmpty()) return;
        if (dialect != PhysicalDialect.DORIS) {
            throw new IllegalArgumentException("引擎 " + dialect + " 不支持物理表属性，请清空后再发布");
        }
        if (dataModel != null && !dataModel.equals("UNIQUE") && !dataModel.equals("DUPLICATE")) {
            throw new IllegalArgumentException("Doris 数据模型仅支持 UNIQUE 或 DUPLICATE");
        }
        if (bucketCount != null && (bucketCount < 1 || bucketCount > MAX_BUCKETS)) {
            throw new IllegalArgumentException("Doris 分桶数须在 1 到 " + MAX_BUCKETS + " 之间");
        }
        if (replicaCount != null && (replicaCount < 1 || replicaCount > MAX_REPLICAS)) {
            throw new IllegalArgumentException("Doris 副本数须在 1 到 " + MAX_REPLICAS + " 之间");
        }
    }

    private static String textField(JsonNode root, String key) {
        var node = root.get(key);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw new IllegalArgumentException("物理属性 " + key + " 必须是字符串");
        var value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer intField(JsonNode root, String key) {
        var node = root.get(key);
        if (node == null || node.isNull()) return null;
        if (!node.canConvertToInt()) throw new IllegalArgumentException("物理属性 " + key + " 必须是整数");
        return node.asInt();
    }
}
