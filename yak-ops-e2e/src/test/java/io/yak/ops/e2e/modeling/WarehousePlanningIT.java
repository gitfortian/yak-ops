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
        assertEquals(
                processId,
                tree.path("categories").get(0).path("children").get(0).path("children").get(0).path("id").asText());
        assertEquals(0, tree.path("datamarts").size());
        assertEquals("NOT_ENABLED", api.call("GET", API + "/processes/" + processId + "/logical-models").path("availability").asText());
        api.expect("DELETE", API + "/categories/" + categoryId + "?revision=1", null, 409);
        api.expect("DELETE", API + "/domains/" + domainId + "?revision=1", null, 409);
        var replacement = new HashMap<String, Object>(input(prefix + "_renamed", owner));
        replacement.put("revision", 1);
        assertEquals(2, api.call("PUT", API + "/processes/" + processId, replacement).path("revision").asInt());
        api.expect("PUT", API + "/processes/" + processId, replacement, 409);
        String other = api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("other"))).path("id").asText();
        api.workspace(other);
        assertEquals(0, api.call("GET", API + "/tree").path("categories").size());
        api.expect("GET", API + "/processes/" + processId + "/logical-models", null, 404);
        api.expect("POST", API + "/categories/" + categoryId + "/domains", input(prefix + "_cross", owner), 404);
        assertFalse(api.call("GET", API + "/codes/availability?code=" + prefix + "_domain").path("available").asBoolean());
        api.workspace(workspace);
        api.expect("POST", API + "/categories", input(prefix + "_domain", owner), 409);
        api.call("DELETE", API + "/processes/" + processId + "?revision=2");
        api.call("DELETE", API + "/domains/" + domainId + "?revision=1");
        api.call("DELETE", API + "/categories/" + categoryId + "?revision=1");
        assertEquals(0, api.call("GET", API + "/tree").path("categories").size());
        assertTrue(api.call("GET", API + "/codes/availability?code=" + prefix + "_renamed").path("available").asBoolean());
    }

    @Test void plansApplicationLayerDatamartsAndSubjects() {
        var api = fixture.api;
        String previous = api.workspace();
        String owner = api.call("GET", "/api/v1/account/current").path("id").asText();
        String prefix = TestNames.unique("mart");
        String workspace = api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("app"))).path("id").asText();
        api.workspace(workspace);

        // A data mart owns an English name and an owner; a subject carries neither owner nor code clash.
        api.expect("POST", API + "/datamarts", Map.of("name", "运营集市", "code", prefix + "_empty", "ownerId", owner), 400);
        var mart = api.call("POST", API + "/datamarts", datamart(prefix + "_mart", owner));
        String martId = mart.path("id").asText();
        var subject = api.call("POST", API + "/datamarts/" + martId + "/subjects", subject(prefix + "_subject"));
        String subjectId = subject.path("id").asText();

        var tree = api.call("GET", API + "/tree");
        assertEquals(0, tree.path("categories").size());
        assertEquals(martId, tree.path("datamarts").get(0).path("id").asText());
        assertEquals("Operating Mart", tree.path("datamarts").get(0).path("enName").asText());
        var subjectNode = tree.path("datamarts").get(0).path("children").get(0);
        assertEquals(subjectId, subjectNode.path("id").asText());
        assertEquals("Realtime Report", subjectNode.path("enName").asText());
        assertFalse(subjectNode.has("ownerId"));

        // Application-layer abbreviations join the same global namespace as business categories.
        api.expect("POST", API + "/categories", input(prefix + "_subject", owner), 409);
        assertFalse(api.call("GET", API + "/codes/availability?code=" + prefix + "_subject")
                .path("available")
                .asBoolean());

        var renamed = new HashMap<String, Object>(subject(prefix + "_renamed"));
        renamed.put("revision", 1);
        assertEquals(2, api.call("PUT", API + "/subjects/" + subjectId, renamed).path("revision").asInt());
        api.expect("PUT", API + "/subjects/" + subjectId, renamed, 409);

        // A data mart keeps its subjects alive, so deletion conflicts until they are gone.
        api.expect("DELETE", API + "/datamarts/" + martId + "?revision=1", null, 409);
        api.call("DELETE", API + "/subjects/" + subjectId + "?revision=2");
        api.call("DELETE", API + "/datamarts/" + martId + "?revision=1");
        assertEquals(0, api.call("GET", API + "/tree").path("datamarts").size());
        assertTrue(api.call("GET", API + "/codes/availability?code=" + prefix + "_renamed")
                .path("available")
                .asBoolean());
        api.workspace(previous);
    }

    private static Map<String, Object> input(String code, String owner) {
        return Map.of("name", "规划节点", "code", code, "ownerId", owner);
    }

    private static Map<String, Object> datamart(String code, String owner) {
        return Map.of("name", "运营集市", "enName", "Operating Mart", "code", code, "ownerId", owner);
    }

    private static Map<String, Object> subject(String code) {
        return Map.of("name", "实时战报", "enName", "Realtime Report", "code", code);
    }
}
