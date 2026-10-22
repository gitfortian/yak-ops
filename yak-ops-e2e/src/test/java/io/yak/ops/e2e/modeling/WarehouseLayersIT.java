package io.yak.ops.e2e.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import io.yak.ops.e2e.support.ApiClient;
import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.testutils.TestNames;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated HTTP warehouse layer presets, binding rules, revision conflicts and isolation. */
class WarehouseLayersIT extends BackendTestBase {
    private static final String API = "/api/v1/data-modeling/warehouse-planning/layers";

    @Test
    void managesLayerPresetsBindingsAndDatasourceProtection() {
        var api = fixture.api;
        String workspace = api.workspace();

        var presets = api.call("GET", API);
        assertEquals(5, presets.size());
        assertEquals("ODS", presets.get(0).path("enCode").asText());
        assertEquals("贴源层", presets.get(0).path("cnName").asText());
        assertTrue(presets.get(0).path("builtin").asBoolean());
        assertFalse(presets.get(0).path("enabled").asBoolean());
        assertTrue(presets.get(0).path("dataSourceId").isNull());
        assertEquals(0, api.call("GET", API + "/options").size());
        String odsId = presets.get(0).path("id").asText();

        var datasourceRow = firstDatasource(api);
        String datasource = datasourceRow.path("id").asText();
        String datasourceName = datasourceRow.path("name").asText();
        assertEquals(2, api.call("GET", API + "/datasources").size());

        // A layer cannot be enabled before it owns a physical datasource.
        api.expect("PUT", API + "/" + odsId, layer("贴源层", "ODS", null, true, 1), 400);
        // Preset identity belongs to the server.
        api.expect("PUT", API + "/" + odsId, layer("贴源改名", "ODS", null, false, 1), 409);
        api.expect("PUT", API + "/" + odsId, layer("贴源层", "ODS_ALT", null, false, 1), 409);

        var bound = api.call("PUT", API + "/" + odsId, layer("贴源层", "ODS", datasource, true, 1));
        assertEquals(2, bound.path("revision").asInt());
        assertEquals(datasourceName, bound.path("dataSourceName").asText());
        assertEquals(1, api.call("GET", API + "/options").size());

        // The foreign key keeps a bound datasource alive and the API reports the conflict.
        api.expect("DELETE", "/api/v1/data-source/" + datasource, null, 409);

        var created = api.call("POST", API, layer("宽表层", "dwd_wide", datasource, true));
        String customId = created.path("id").asText();
        assertEquals("DWD_WIDE", created.path("enCode").asText());
        assertFalse(created.path("builtin").asBoolean());
        assertEquals(2, api.call("GET", API + "/options").size());
        api.expect("POST", API, layer("重复层", "DWD_WIDE", datasource, false), 409);
        api.expect("POST", API, layer("非法层", "2_LAYER", null, false), 400);
        api.expect("POST", API, layer("无源层", "NO_SRC", null, true), 400);

        assertFalse(api.call("GET", API + "/codes/availability?enCode=DWD_WIDE")
                .path("available")
                .asBoolean());
        assertTrue(api.call("GET", API + "/codes/availability?enCode=dwd_wide&excludeId=" + customId)
                .path("available")
                .asBoolean());
        assertTrue(api.call("GET", API + "/codes/availability?enCode=ODS_FREE")
                .path("available")
                .asBoolean());

        api.expect("DELETE", API + "/" + odsId + "?revision=2", null, 409);
        api.expect("DELETE", API + "/" + customId + "?revision=9", null, 409);
        api.call("DELETE", API + "/" + customId + "?revision=1");
        api.expect("DELETE", API + "/" + customId + "?revision=1", null, 404);

        String other = api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("other")))
                .path("id")
                .asText();
        api.workspace(other);
        assertEquals(5, api.call("GET", API).size());
        assertEquals(0, api.call("GET", API + "/options").size());
        api.expect("PUT", API + "/" + odsId, layer("贴源层", "ODS", null, false, 1), 404);
        api.workspace(workspace);
        assertEquals(5, api.call("GET", API).size());
    }

    private static JsonNode firstDatasource(ApiClient api) {
        for (JsonNode row : api.call("POST", "/api/v1/data-source/page", Map.of("pageNo", 1, "pageSize", 100))
                .path("bizData")) {
            if (row.path("dbType").asText().equals("MYSQL")) return row;
        }
        throw new AssertionError("The fixture datasource was not returned in its Workspace");
    }

    private static Map<String, Object> layer(
            String cnName, String enCode, String dataSourceId, boolean enabled) {
        var body = new LinkedHashMap<String, Object>();
        body.put("cnName", cnName);
        body.put("enCode", enCode);
        body.put("dataSourceId", dataSourceId);
        body.put("description", null);
        body.put("enabled", enabled);
        return body;
    }

    private static Map<String, Object> layer(
            String cnName, String enCode, String dataSourceId, boolean enabled, long revision) {
        var body = new LinkedHashMap<>(layer(cnName, enCode, dataSourceId, enabled));
        body.put("revision", revision);
        return body;
    }
}
