package io.yak.ops.plugin.task.datasync.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.connector.SyncConnectorFactoryRegistry;
import io.yak.ops.plugin.task.datasync.sink.SinkParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Decodes the saved DATA_SYNC JSON using the parameter types declared by Connector SPI factories.
 *
 * <p>The persisted source/transform/sink contract and its type identifiers remain unchanged.
 * Unrecognized JSON members are rejected before they can become executable parameters.
 */
public final class SyncParameterCodec {

    private static final Set<String> FIELDS = Set.of("source", "transform", "sink");
    private final SyncConnectorFactoryRegistry factories;

    public SyncParameterCodec(SyncConnectorFactoryRegistry factories) {
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    /**
     * Decodes one Definition without exposing untrusted JSON in diagnostics.
     *
     * @param content pinned Definition JSON
     * @return resolved DATA_SYNC parameters
     */
    public SyncParameters parse(String content) {
        JsonNode document = JSONUtils.readTree(content);
        if (!(document instanceof ObjectNode)) {
            throw new IllegalArgumentException("DATA_SYNC definition must be an object");
        }
        document.fieldNames().forEachRemaining(name -> {
            if (!FIELDS.contains(name)) {
                throw new IllegalArgumentException("Unexpected DATA_SYNC definition field");
            }
        });
        SourceParameters source = source(document.get("source"));
        SinkParameters sink = sink(document.get("sink"));
        JsonNode transform = document.get("transform");
        List<JsonNode> steps = new ArrayList<>();
        if (transform != null && !transform.isNull()) {
            if (!transform.isArray()) {
                throw new IllegalArgumentException("DATA_SYNC transform must be an array");
            }
            transform.forEach(steps::add);
        }
        return new SyncParameters(source, steps, sink);
    }

    private SourceParameters source(JsonNode node) {
        var factory = factories.source(type(node));
        return JSONUtils.parseObject(payload(node), factory.parametersType());
    }

    private SinkParameters sink(JsonNode node) {
        var factory = factories.sink(type(node));
        return JSONUtils.parseObject(payload(node), factory.parametersType());
    }

    private static String type(JsonNode node) {
        if (!(node instanceof ObjectNode) || !node.hasNonNull("type") || !node.get("type").isTextual()) {
            throw new IllegalArgumentException("DATA_SYNC endpoint type is required");
        }
        String value = node.get("type").textValue();
        if (value.isBlank()) {
            throw new IllegalArgumentException("DATA_SYNC endpoint type is required");
        }
        return value;
    }

    private static String payload(JsonNode node) {
        ObjectNode value = ((ObjectNode) node).deepCopy();
        value.remove("type");
        return value.toString();
    }
}
