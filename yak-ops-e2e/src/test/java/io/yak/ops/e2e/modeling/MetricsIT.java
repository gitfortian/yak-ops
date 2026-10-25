package io.yak.ops.e2e.modeling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.e2e.support.ApiClient;
import io.yak.ops.e2e.support.BackendTestBase;
import io.yak.ops.testutils.TestNames;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Authenticated HTTP metric lifecycle: five kinds, composition, references and delete protection. */
class MetricsIT extends BackendTestBase {
    private static final String API = "/api/v1/data-modeling/metrics";
    private static final String PLANNING = "/api/v1/data-modeling/warehouse-planning";

    @Test
    void managesTheFiveMetricKindsWithCompositionReferencesAndDeleteProtection() {
        var api = fixture.api;
        String workspace = api.workspace();
        String owner = api.call("GET", "/api/v1/account/current").path("id").asText();
        String prefix = TestNames.unique("metric");

        // Planning anchors: a modifier hangs under a domain, an atomic metric under a process.
        String categoryId = api.call(
                        "POST",
                        PLANNING + "/categories",
                        Map.of("name", "电商业务", "code", prefix + "_cat", "ownerId", owner))
                .path("id")
                .asText();
        String domainId = api.call(
                        "POST",
                        PLANNING + "/categories/" + categoryId + "/domains",
                        Map.of("name", "交易域", "code", prefix + "_domain", "ownerId", owner))
                .path("id")
                .asText();
        String processId = api.call(
                        "POST",
                        PLANNING + "/domains/" + domainId + "/processes",
                        Map.of("name", "下单", "code", prefix + "_process", "ownerId", owner))
                .path("id")
                .asText();

        // Time periods: flat dictionary with a Workspace-unique abbreviation.
        api.expect("POST", API + "/time-periods", period("Invalid_Code", "大写"), 400);
        String periodId = api.call("POST", API + "/time-periods", period("d7", "最近7天")).path("id").asText();
        assertFalse(api.call("GET", API + "/time-periods/codes/availability?enCode=d7")
                .path("available")
                .asBoolean());
        assertTrue(api.call("GET", API + "/time-periods/codes/availability?enCode=d1")
                .path("available")
                .asBoolean());
        assertEquals("最近7天", api.call("GET", API + "/time-periods/" + periodId).path("cnName").asText());

        // Modifiers anchor to a data domain; an unknown domain is not found.
        api.expect("POST", API + "/modifiers", modifier("missing_domain", "muying", "母婴"), 404);
        String modifierId = api.call("POST", API + "/modifiers", modifier(domainId, "muying", "母婴"))
                .path("id")
                .asText();

        // Atomic metrics enforce the aggregate/type matrix; a SUM over a STRING cannot exist.
        api.expect("POST", API + "/atomic-metrics", atomic(processId, "pay_amt", "STRING", "SUM", owner), 400);
        String atomicId = api.call(
                        "POST",
                        API + "/atomic-metrics",
                        atomic(processId, "pay_amt", "DECIMAL", "SUM", owner))
                .path("id")
                .asText();
        assertFalse(api.call("GET", API + "/atomic-metrics/codes/availability?enCode=pay_amt")
                .path("available")
                .asBoolean());

        // Derived metrics compose code and name from the parts; identical parts collide.
        String composition = "d7_muying_pay_amt";
        var derivedBody = derived(atomicId, periodId, List.of(modifierId));
        String derivedId = api.call("POST", API + "/derived-metrics", derivedBody).path("id").asText();
        var stored = api.call("GET", API + "/derived-metrics/" + derivedId);
        assertEquals(composition, stored.path("enCode").asText());
        assertEquals("最近7天母婴支付金额", stored.path("cnName").asText());
        assertFalse(api.call("GET", API + "/derived-metrics/codes/availability?atomicMetricId=" + atomicId
                        + "&periodId=" + periodId + "&modifierIds=" + modifierId)
                .path("available")
                .asBoolean());
        api.expect("POST", API + "/derived-metrics", derivedBody, 409);

        // Composite metrics store the submitted expression and resolve references to derived codes.
        api.expect("POST", API + "/composite-metrics", composite("growth", composition + " + ghost"), 400);
        api.expect("POST", API + "/composite-metrics", composite("growth", composition + " +"), 400);
        String compositeId = api.call(
                        "POST", API + "/composite-metrics", composite("growth", composition + " * 100"))
                .path("id")
                .asText();
        assertEquals(
                composition + " * 100",
                api.call("GET", API + "/composite-metrics/" + compositeId).path("expression").asText());

        // The reference panel answers for the delete dialog; deletion stays restricted until clear.
        var panel = api.call("GET", API + "/derived-metrics/" + derivedId + "/references");
        assertEquals(1, panel.path("count").asInt());
        assertEquals("COMPOSITE_METRIC", panel.path("items").get(0).path("kind").asText());
        api.expect("DELETE", API + "/derived-metrics/" + derivedId + "?revision=1", null, 409);
        api.expect("DELETE", API + "/modifiers/" + modifierId + "?revision=1", null, 409);

        // Editing the parts recomposes the identity under the observed revision.
        String periodId2 = api.call("POST", API + "/time-periods", period("d1", "昨日")).path("id").asText();
        var replacement = derived(atomicId, periodId2, List.of());
        replacement.put("revision", 1);
        assertEquals(
                2,
                api.call("PUT", API + "/derived-metrics/" + derivedId, replacement).path("revision").asInt());
        assertEquals(
                "d1_pay_amt",
                api.call("GET", API + "/derived-metrics/" + derivedId).path("enCode").asText());
        api.expect("PUT", API + "/derived-metrics/" + derivedId, replacement, 409);

        // The composite that cites it follows in the same transaction: text and revision move together.
        var rewritten = api.call("GET", API + "/composite-metrics/" + compositeId);
        assertEquals("d1_pay_amt * 100", rewritten.path("expression").asText());
        assertEquals(2, rewritten.path("revision").asInt());

        // A stale composite revision conflicts; deletion cascades and releases the derived metric.
        api.expect("DELETE", API + "/composite-metrics/" + compositeId + "?revision=9", null, 409);
        api.expect("DELETE", API + "/composite-metrics/" + compositeId + "?revision=1", null, 409);
        api.call("DELETE", API + "/composite-metrics/" + compositeId + "?revision=2");
        api.call("DELETE", API + "/derived-metrics/" + derivedId + "?revision=2");
        api.expect("GET", API + "/derived-metrics/" + derivedId, null, 404);
        api.call("DELETE", API + "/modifiers/" + modifierId + "?revision=1");
        api.call("DELETE", API + "/atomic-metrics/" + atomicId + "?revision=1");
        api.call("DELETE", API + "/time-periods/" + periodId + "?revision=1");
        api.call("DELETE", API + "/time-periods/" + periodId2 + "?revision=1");

        // Another Workspace never sees the metrics created above.
        String other = api.call("POST", "/api/v1/workspaces", Map.of("name", TestNames.unique("other")))
                .path("id")
                .asText();
        api.workspace(other);
        assertEquals(
                0,
                api.call("GET", API + "/time-periods?pageNo=1&pageSize=20")
                        .path("pagination")
                        .path("total")
                        .asInt());
        api.expect("GET", API + "/derived-metrics/" + derivedId, null, 404);
        api.workspace(workspace);
    }

    private static Map<String, Object> period(String enCode, String cnName) {
        return Map.of("enCode", enCode, "cnName", cnName);
    }

    private static Map<String, Object> modifier(String domainId, String enCode, String cnName) {
        return Map.of("domainId", domainId, "enCode", enCode, "cnName", cnName);
    }

    private static Map<String, Object> atomic(
            String processId, String enCode, String dataType, String aggregateType, String ownerId) {
        return Map.of(
                "processId", processId,
                "enCode", enCode,
                "cnName", "支付金额",
                "dataType", dataType,
                "aggregateType", aggregateType,
                "ownerId", ownerId);
    }

    private static HashMap<String, Object> derived(String atomicMetricId, String periodId, List<String> modifierIds) {
        var body = new HashMap<String, Object>();
        body.put("atomicMetricId", atomicMetricId);
        body.put("periodId", periodId);
        body.put("modifierIds", modifierIds);
        return body;
    }

    private static Map<String, Object> composite(String enCode, String expression) {
        return Map.of("enCode", enCode, "cnName", "增长率", "expression", expression);
    }
}
