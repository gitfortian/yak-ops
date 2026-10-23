package io.yak.ops.e2e.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.e2e.support.ApiClient;
import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.testutils.TestNames;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated HTTP concept dimension and logical model contracts, scoping and Workspace isolation. */
class DimensionModelingIT extends BackendTestBase {
    private static final String API = "/api/v1/data-modeling/dimension-modeling";
    private static final String PLANNING = "/api/v1/data-modeling/warehouse-planning";
    private static final String LAYERS = PLANNING + "/layers";
    private static final String STANDARDS = "/api/v1/data-modeling/data-standards";

    @Test
    void scopesDimensionsByDomainOrDataMart() {
        var api = fixture.api;
        String previous = api.workspace();
        String owner = api.call("GET", "/api/v1/account/current").path("id").asText();
        String prefix = TestNames.unique("dim");
        String workspace = api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("dim"))).path("id").asText();
        api.workspace(workspace);

        String categoryId = api.call("POST", PLANNING + "/categories", planning(prefix + "_cat", owner)).path("id").asText();
        String tradeDomain = api.call("POST", PLANNING + "/categories/" + categoryId + "/domains",
                planning(prefix + "_trade", owner)).path("id").asText();
        String salesDomain = api.call("POST", PLANNING + "/categories/" + categoryId + "/domains",
                planning(prefix + "_sales", owner)).path("id").asText();
        String datamartId = api.call("POST", PLANNING + "/datamarts",
                Map.of("name", "运营集市", "enName", "Operating Mart", "code", prefix + "_mart", "ownerId", owner))
                .path("id").asText();

        // Scope rules: the identifier of the selected scope is required and the other one is ignored.
        api.expect("POST", API + "/dimensions", dimension("PUBLIC", null, null, prefix + "_missing"), 400);
        api.expect("POST", API + "/dimensions", dimension("APPLICATION", tradeDomain, null, prefix + "_wrong"), 400);

        var created = api.call("POST", API + "/dimensions",
                dimension("PUBLIC", tradeDomain, datamartId, prefix + "_product"));
        String dimensionId = created.path("id").asText();
        assertEquals("PUBLIC", created.path("scope").asText());
        assertEquals(tradeDomain, created.path("domainId").asText());
        assertFalse(created.has("datamartId"));
        assertFalse(created.path("domainName").asText().isBlank());

        // The same abbreviation is allowed in another scope but not twice inside one scope.
        api.call("POST", API + "/dimensions", dimension("PUBLIC", salesDomain, null, prefix + "_product"));
        api.expect("POST", API + "/dimensions", dimension("PUBLIC", tradeDomain, null, prefix + "_product"), 409);
        api.call("POST", API + "/dimensions", dimension("APPLICATION", null, datamartId, prefix + "_product"));
        api.expect("POST", API + "/dimensions", dimension("PUBLIC", tradeDomain, null, prefix + "_Product"), 400);

        assertEquals(1, api.call("GET", API + "/dimensions?scope=APPLICATION").size());
        assertEquals(2, api.call("GET", API + "/dimensions?scope=PUBLIC").size());
        assertEquals(1, api.call("GET", API + "/dimensions?domainId=" + salesDomain).size());

        var renamed = dimension("PUBLIC", tradeDomain, null, prefix + "_product_sk");
        renamed.put("revision", 1);
        assertEquals(2, api.call("PUT", API + "/dimensions/" + dimensionId, renamed).path("revision").asInt());
        api.expect("PUT", API + "/dimensions/" + dimensionId, renamed, 409);

        // Ownership is immutable: moving the dimension to a data mart is rejected.
        var moved = dimension("APPLICATION", null, datamartId, prefix + "_product_sk");
        moved.put("revision", 2);
        api.expect("PUT", API + "/dimensions/" + dimensionId, moved, 400);

        // The foreign key keeps the owning scope alive: deleting the domain while a dimension exists conflicts.
        api.expect("DELETE", PLANNING + "/domains/" + tradeDomain + "?revision=1", null, 409);

        api.call("DELETE", API + "/dimensions/" + dimensionId + "?revision=2");
        api.expect("DELETE", API + "/dimensions/" + dimensionId + "?revision=2", null, 404);

        // Another Workspace sees none of it and cannot address the rows by id.
        api.workspace(previous);
        boolean visible = false;
        for (var row : api.call("GET", API + "/dimensions")) visible |= dimensionId.equals(row.path("id").asText());
        assertFalse(visible);
        api.expect("DELETE", API + "/dimensions/" + dimensionId + "?revision=2", null, 404);
    }

    private static Map<String, Object> planning(String code, String owner) {
        return Map.of("name", "规划节点", "code", code, "ownerId", owner);
    }

    @Test
    void buildsLogicalModelsWithInheritedFieldStandards() {
        var api = fixture.api;
        String previous = api.workspace();
        String owner = api.call("GET", "/api/v1/account/current").path("id").asText();
        String prefix = TestNames.unique("model");
        String workspace =
                api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("mdl")))
                        .path("id")
                        .asText();
        api.workspace(workspace);

        String categoryId =
                api.call("POST", PLANNING + "/categories", planning(prefix + "_cat", owner)).path("id").asText();
        String domainId = api.call(
                        "POST", PLANNING + "/categories/" + categoryId + "/domains", planning(prefix + "_trade", owner))
                .path("id")
                .asText();
        String processId = api.call(
                        "POST", PLANNING + "/domains/" + domainId + "/processes", planning(prefix + "_order", owner))
                .path("id")
                .asText();

        // A layer is only selectable once enabled and bound to a physical datasource.
        String layerId = layer(api, "DWD");
        api.expect(
                "POST",
                API + "/models",
                detailModel(prefix + "_blocked", layerId, categoryId, domainId, processId, List.of()),
                422);
        api.call("PUT", LAYERS + "/" + layerId, layerBinding("明细层", "DWD", firstDatasource(api), 1));
        String suffix = prefix + "_detail";
        String name = "dwd_" + suffix;

        api.call("POST", STANDARDS + "/catalog/initialize");
        String standardCategory =
                api.call("POST", STANDARDS + "/categories", Map.of("name", "交易")).path("id").asText();
        String standardId = api.call(
                        "POST",
                        STANDARDS + "/categories/" + standardCategory + "/fields",
                        standard(prefix + "_order_id", owner))
                .path("id")
                .asText();

        // Scope and naming rules answer before anything is written.
        api.expect("POST", API + "/models", detailModel(suffix, layerId, categoryId, domainId, null, List.of()), 400);
        api.expect(
                "POST",
                API + "/models",
                detailModel("Invalid-Suffix", layerId, categoryId, domainId, processId, List.of()),
                400);
        api.expect(
                "POST",
                API + "/models",
                detailModel(
                        suffix,
                        layerId,
                        categoryId,
                        domainId,
                        processId,
                        List.of(partitionField("ds", "BIGINT"))),
                400);
        api.expect(
                "POST",
                API + "/models",
                detailModel(
                        suffix,
                        layerId,
                        categoryId,
                        domainId,
                        processId,
                        List.of(partitionField("ds", "STRING"), partitionField("region", "STRING"))),
                400);

        // A bound standard overwrites the code and type; submitted nullability is kept as sent.
        var created = api.call(
                "POST",
                API + "/models",
                detailModel(
                        suffix,
                        layerId,
                        categoryId,
                        domainId,
                        processId,
                        List.of(standardField("placeholder", standardId), partitionField("ds", "STRING"))));
        String modelId = created.path("id").asText();
        assertEquals("DWD", created.path("type").asText());
        assertEquals(name, created.path("name").asText());
        assertEquals(2, created.path("fields").size());
        var inherited = created.path("fields").get(0);
        assertEquals(prefix + "_order_id", inherited.path("enCode").asText());
        assertEquals("BIGINT", inherited.path("dataType").asText());
        assertFalse(inherited.path("isNotNull").asBoolean());
        assertTrue(inherited.path("isPrimaryKey").asBoolean());

        assertFalse(api.call(
                        "GET", API + "/models/name-availability?layerId=" + layerId + "&nameSuffix=" + suffix)
                .asBoolean());
        api.expect(
                "POST", API + "/models", detailModel(suffix, layerId, categoryId, domainId, processId, List.of()), 409);

        assertEquals(
                1, modelsPage(api, "type=DWD&keyword=" + suffix).path("pagination").path("total").asInt());
        assertEquals(1, modelsPage(api, "domainId=" + domainId).path("pagination").path("total").asInt());
        assertEquals(0, modelsPage(api, "type=DIM").path("pagination").path("total").asInt());

        // A public-layer dimension table must bind a dimension of its own domain.
        String dimensionId = api.call(
                        "POST", API + "/dimensions", dimension("PUBLIC", domainId, null, prefix + "_product"))
                .path("id")
                .asText();
        api.expect("POST", API + "/models", dimensionTable(prefix + "_no_dim", layerId, categoryId, domainId), 400);
        var dimensionTable = api.call(
                "POST",
                API + "/models",
                dimensionTableWithDimension(prefix + "_product", layerId, categoryId, domainId, dimensionId));
        assertEquals("DIM", dimensionTable.path("type").asText());
        // The prefix always follows the selected layer, never the model type.
        assertEquals("dwd_" + prefix + "_product", dimensionTable.path("name").asText());

        // Updating replaces the whole field list and advances the revision.
        var renamed = detailModel(
                suffix + "_v2",
                layerId,
                categoryId,
                domainId,
                processId,
                List.of(standardField("placeholder", standardId)));
        renamed.put("revision", 1);
        var updated = api.call("PUT", API + "/models/" + modelId, renamed);
        assertEquals(2, updated.path("revision").asInt());
        assertEquals("dwd_" + suffix + "_v2", updated.path("name").asText());
        assertEquals(1, updated.path("fields").size());
        api.expect("PUT", API + "/models/" + modelId, renamed, 409);

        api.call("DELETE", API + "/models/" + modelId + "?revision=2");
        api.expect("DELETE", API + "/models/" + modelId + "?revision=2", null, 404);

        // Another Workspace cannot read the remaining dimension table by id.
        api.workspace(previous);
        api.expect("GET", API + "/models/" + dimensionTable.path("id").asText(), null, 404);
    }

    private static Map<String, Object> dimension(String scope, String domainId, String datamartId, String code) {
        var body = new LinkedHashMap<String, Object>();
        body.put("scope", scope);
        body.put("domainId", domainId);
        body.put("datamartId", datamartId);
        body.put("name", "商品");
        body.put("enName", "Product");
        body.put("code", code);
        return body;
    }

    private static Map<String, Object> detailModel(
            String suffix,
            String layerId,
            String categoryId,
            String domainId,
            String processId,
            List<Map<String, Object>> fields) {
        var body = modelBody("DWD", suffix, layerId, categoryId, fields);
        body.put("domainId", domainId);
        body.put("processId", processId);
        return body;
    }

    private static Map<String, Object> dimensionTable(
            String suffix, String layerId, String categoryId, String domainId) {
        var body = modelBody("DIM", suffix, layerId, categoryId, List.of());
        body.put("domainId", domainId);
        return body;
    }

    private static Map<String, Object> dimensionTableWithDimension(
            String suffix, String layerId, String categoryId, String domainId, String dimensionId) {
        var body = dimensionTable(suffix, layerId, categoryId, domainId);
        body.put("dimensionId", dimensionId);
        return body;
    }

    private static Map<String, Object> modelBody(
            String type, String suffix, String layerId, String categoryId, List<Map<String, Object>> fields) {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", type);
        body.put("layerId", layerId);
        body.put("categoryId", categoryId);
        body.put("nameSuffix", suffix);
        body.put("cnName", "订单明细");
        body.put("storagePolicy", "DAILY_INCREMENTAL");
        body.put("lifecycleDays", 30);
        body.put("fields", fields);
        return body;
    }

    private static Map<String, Object> standardField(String enCode, String standardId) {
        var field = new LinkedHashMap<String, Object>();
        field.put("enCode", enCode);
        field.put("cnName", "订单号");
        field.put("standardId", standardId);
        field.put("isPrimaryKey", true);
        field.put("isNotNull", false);
        return field;
    }

    private static Map<String, Object> partitionField(String enCode, String dataType) {
        var field = new LinkedHashMap<String, Object>();
        field.put("enCode", enCode);
        field.put("cnName", "分区日期");
        field.put("dataType", dataType);
        field.put("isPartition", true);
        return field;
    }

    private static Map<String, Object> standard(String enCode, String owner) {
        return Map.of(
                "cnName", "订单号",
                "enCode", enCode,
                "dataType", "BIGINT",
                "lengthPrecision", "20",
                "isNotNull", true,
                "securityLevel", "INTERNAL",
                "ownerId", owner);
    }

    private static Map<String, Object> layerBinding(
            String cnName, String enCode, String dataSourceId, long revision) {
        var body = new LinkedHashMap<String, Object>();
        body.put("cnName", cnName);
        body.put("enCode", enCode);
        body.put("dataSourceId", dataSourceId);
        body.put("description", null);
        body.put("enabled", true);
        body.put("revision", revision);
        return body;
    }

    private static String layer(ApiClient api, String enCode) {
        for (var row : api.call("GET", LAYERS)) {
            if (enCode.equals(row.path("enCode").asText())) return row.path("id").asText();
        }
        throw new AssertionError("The preset layer was not returned: " + enCode);
    }

    private static String firstDatasource(ApiClient api) {
        for (var row : api.call("POST", "/api/v1/data-source/page", Map.of("pageNo", 1, "pageSize", 100))
                .path("bizData")) {
            if (row.path("dbType").asText().equals("MYSQL")) return row.path("id").asText();
        }
        throw new AssertionError("The fixture datasource was not returned in its Workspace");
    }

    private static JsonNode modelsPage(ApiClient api, String query) {
        return api.call("GET", API + "/models?pageNo=1&pageSize=20&" + query);
    }
}
