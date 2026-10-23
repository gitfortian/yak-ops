package io.yak.ops.e2e.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.testutils.TestNames;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated HTTP concept dimension scope rules, per-scope uniqueness and Workspace isolation. */
class DimensionModelingIT extends BackendTestBase {
    private static final String API = "/api/v1/data-modeling/dimension-modeling";
    private static final String PLANNING = "/api/v1/data-modeling/warehouse-planning";

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
}
