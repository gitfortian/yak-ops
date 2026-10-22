package io.yak.ops.e2e.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.e2e.support.ApiClient;
import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.testutils.TestNames;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated HTTP standard catalog lifecycle, search escaping, revision and Workspace isolation. */
class DataStandardsIT extends BackendTestBase {
    private static final String API = "/api/v1/data-modeling/data-standards";

    @Test
    void managesStandardCatalogLifecycleAndIsolatesWorkspaces() {
        var api = fixture.api;
        String workspace = api.workspace();
        String owner = api.call("GET", "/api/v1/account/current").path("id").asText();
        String prefix = TestNames.unique("standard");

        assertEquals(2, api.call("POST", API + "/catalog/initialize").size());
        assertEquals(2, api.call("POST", API + "/catalog/initialize").size());
        assertEquals(2, api.call("GET", API + "/categories/tree").size());

        var owners = api.call("GET", API + "/owners");
        assertEquals(owner, owners.get(0).path("userId").asText());
        assertFalse(owners.get(0).has("email"));
        assertFalse(owners.get(0).has("userName"));

        api.expect("POST", API + "/categories", Map.of("name", "  "), 400);

        String categoryId =
                api.call("POST", API + "/categories", Map.of("name", "交易类")).path("id").asText();
        String childId = api.call("POST", API + "/categories", Map.of("parentId", categoryId, "name", "订单"))
                .path("id")
                .asText();
        String firstId = api.call(
                        "POST", API + "/categories/" + childId + "/fields", input(prefix + "_one", owner, "订单_明细"))
                .path("id")
                .asText();
        api.expect("POST", API + "/categories/" + childId + "/fields", input("Invalid_Code", owner, "订单"), 400);

        var read = api.call("GET", API + "/fields/" + firstId);
        assertEquals("交易类 / 订单", read.path("categoryPath").asText());
        assertEquals(prefix + "_one", read.path("enCode").asText());
        assertEquals("INTERNAL", read.path("securityLevel").asText());
        assertEquals("NOT_ENABLED", api.call("GET", API + "/fields/" + firstId + "/references").path("availability").asText());
        assertFalse(api.call("GET", API + "/fields/codes/availability?enCode=" + prefix + "_one")
                .path("available")
                .asBoolean());
        assertTrue(api.call("GET", API + "/fields/codes/availability?enCode=" + prefix + "_free")
                .path("available")
                .asBoolean());

        String secondId = api.call(
                        "POST", API + "/categories/" + childId + "/fields", input(prefix + "_two", owner, "订单X明细"))
                .path("id")
                .asText();
        String thirdId = api.call(
                        "POST", API + "/categories/" + childId + "/fields", input(prefix + "_three", owner, "折扣%金额"))
                .path("id")
                .asText();
        assertEquals(
                3,
                api.call("GET", API + "/fields?categoryId=" + childId + "&pageNo=1&pageSize=20")
                        .path("pagination")
                        .path("total")
                        .asInt());
        assertEquals(1, search(api, "订单_"));
        assertEquals(2, search(api, "订单"));
        assertEquals(1, search(api, "%"));

        assertEquals(2, api.call("PUT", API + "/fields/" + firstId, update(prefix + "_one", owner, "订单_金额", 1))
                .path("revision")
                .asInt());
        api.expect("PUT", API + "/fields/" + firstId, update(prefix + "_one", owner, "订单_金额", 1), 409);
        api.expect("PUT", API + "/fields/" + firstId, update("Invalid_Code", owner, "订单_金额", 2), 400);

        String other = api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("other")))
                .path("id")
                .asText();
        api.workspace(other);
        assertEquals(0, api.call("GET", API + "/categories/tree").size());
        api.expect("GET", API + "/fields/" + firstId, null, 404);
        api.expect("POST", API + "/categories/" + childId + "/fields", input(prefix + "_cross", owner, "跨空间"), 404);
        api.workspace(workspace);

        api.expect("DELETE", API + "/categories/" + childId + "?revision=1", null, 409);
        api.call("DELETE", API + "/fields/" + firstId + "?revision=2");
        api.call("DELETE", API + "/fields/" + secondId + "?revision=1");
        api.call("DELETE", API + "/fields/" + thirdId + "?revision=1");
        api.expect("DELETE", API + "/fields/" + secondId + "?revision=1", null, 404);
        api.call("DELETE", API + "/categories/" + childId + "?revision=1");
        api.call("DELETE", API + "/categories/" + categoryId + "?revision=1");
        assertEquals(2, api.call("GET", API + "/categories/tree").size());
        assertEquals(0, search(api, "订单"));
    }

    private static int search(ApiClient api, String keyword) {
        return api.call(
                        "GET",
                        API + "/fields?keyword="
                                + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                                + "&pageNo=1&pageSize=20")
                .path("pagination")
                .path("total")
                .asInt();
    }

    private static Map<String, Object> input(String code, String owner, String name) {
        return Map.of(
                "cnName", name,
                "enCode", code,
                "dataType", "STRING",
                "isNotNull", false,
                "securityLevel", "INTERNAL",
                "ownerId", owner);
    }

    private static Map<String, Object> update(String code, String owner, String name, long revision) {
        var body = new HashMap<String, Object>(input(code, owner, name));
        body.put("revision", revision);
        return body;
    }
}
