package io.yak.ops.e2e.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.testutils.TestNames;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated HTTP planning lifecycle, Workspace isolation and revision/dependency errors. */
class WarehousePlanningIT extends BackendTestBase {
    private static final String API = "/api/v1/data-modeling/warehouse-planning";

    @Test void plansThreeLevelsWithScopedOwnershipAndProtectedDeletion() {
        var api = fixture.api;
        String workspace = api.workspace();
        String owner = api.call("GET", "/api/v1/account/current").path("id").asText();
        String prefix = TestNames.unique("planning");
        var owners = api.call("GET", API + "/owners");
        assertEquals(owner, owners.get(0).path("userId").asText());
        assertFalse(owners.get(0).has("email"));
        api.expect("POST", API + "/categories", input(prefix + "_invalid", "not-a-member"), 400);
        var category = api.call("POST", API + "/categories", input(prefix + "_category", owner));
        String categoryId = category.path("id").asText();
        var domain = api.call("POST", API + "/categories/" + categoryId + "/domains", input(prefix + "_domain", owner));
        String domainId = domain.path("id").asText();
        var process = api.call("POST", API + "/domains/" + domainId + "/processes", input(prefix + "_process", owner));
        String processId = process.path("id").asText();
        var tree = api.call("GET", API + "/tree");
        assertEquals(processId, tree.get(0).path("children").get(0).path("children").get(0).path("id").asText());
        assertEquals("NOT_ENABLED", api.call("GET", API + "/processes/" + processId + "/logical-models").path("availability").asText());
        api.expect("DELETE", API + "/categories/" + categoryId + "?revision=1", null, 409);
        api.expect("DELETE", API + "/domains/" + domainId + "?revision=1", null, 409);
        var replacement = new HashMap<String, Object>(input(prefix + "_renamed", owner));
        replacement.put("revision", 1);
        assertEquals(2, api.call("PUT", API + "/processes/" + processId, replacement).path("revision").asInt());
        api.expect("PUT", API + "/processes/" + processId, replacement, 409);
        String other = api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("other"))).path("id").asText();
        api.workspace(other);
        assertEquals(0, api.call("GET", API + "/tree").size());
        api.expect("GET", API + "/processes/" + processId + "/logical-models", null, 404);
        api.expect("POST", API + "/categories/" + categoryId + "/domains", input(prefix + "_cross", owner), 404);
        assertFalse(api.call("GET", API + "/codes/availability?code=" + prefix + "_domain").path("available").asBoolean());
        api.workspace(workspace);
        api.expect("POST", API + "/categories", input(prefix + "_domain", owner), 409);
        api.call("DELETE", API + "/processes/" + processId + "?revision=2");
        api.call("DELETE", API + "/domains/" + domainId + "?revision=1");
        api.call("DELETE", API + "/categories/" + categoryId + "?revision=1");
        assertEquals(0, api.call("GET", API + "/tree").size());
        assertTrue(api.call("GET", API + "/codes/availability?code=" + prefix + "_renamed").path("available").asBoolean());
    }

    private static Map<String, Object> input(String code, String owner) {
        return Map.of("name", "规划节点", "code", code, "ownerId", owner);
    }
}
