package io.yak.ops.business.modeling.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardErrorCode;
import io.yak.ops.common.enums.modeling.standard.StandardKind;
import io.yak.ops.common.enums.modeling.standard.StandardNodeType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.modeling.standard.CategoryEntity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The field and page rules the four standard kinds share. */
class StandardCatalogTest {
    @Test
    void acceptsALowercaseCodeAndRefusesEverythingElse() {
        assertEquals("order_status", StandardCatalog.validCode("order_status"));
        for (String rejected : List.of("Order", "1order", "order-status", "", "order status")) {
            var error = assertThrows(BusinessException.class, () -> StandardCatalog.validCode(rejected));
            assertEquals(StandardErrorCode.INVALID_CODE, error.getErrorCode());
        }
        assertThrows(BusinessException.class, () -> StandardCatalog.validCode("a".repeat(65)));
    }

    @Test
    void stripsDisplayNamesAndRefusesBlanks() {
        assertEquals("订单状态", StandardCatalog.validName(" 订单状态 "));
        for (String rejected : new String[] {null, "   ", "a".repeat(129)}) {
            assertThrows(BusinessException.class, () -> StandardCatalog.validName(rejected));
        }
    }

    @Test
    void dropsABlankOptionalTextAndKeepsTheTrimmedValue() {
        assertNull(StandardCatalog.optionalText("   ", 10));
        assertNull(StandardCatalog.optionalText(null, 10));
        assertEquals("说明", StandardCatalog.optionalText("  说明 ", 10));
        assertThrows(BusinessException.class, () -> StandardCatalog.optionalText("a".repeat(11), 10));
    }

    @Test
    void escapesWildcardsInTheSearchPattern() {
        assertEquals("%50!%!_off%", StandardCatalog.keyword("50%_off"));
        assertEquals("%order%", StandardCatalog.keyword("  ORDER "));
        assertNull(StandardCatalog.keyword(null));
        assertThrows(BusinessException.class, () -> StandardCatalog.keyword(" "));
    }

    @Test
    void boundsThePageRequest() {
        assertEquals(40L, StandardCatalog.offset(3, 20));
        assertThrows(BusinessException.class, () -> StandardCatalog.offset(0, 20));
        assertThrows(BusinessException.class, () -> StandardCatalog.offset(1, 101));
    }

    @Test
    void buildsTheRootFirstPath() {
        var rows = Map.of(
                "root", node("root", null, "公共标准"),
                "child", node("child", "root", "交易域"));
        assertEquals(List.of("公共标准", "交易域"), StandardCatalog.path("child", rows));
    }

    @Test
    void refusesAPathThatLoops() {
        var rows = Map.of(
                "a", node("a", "b", "A"),
                "b", node("b", "a", "B"));
        assertThrows(IllegalStateException.class, () -> StandardCatalog.path("a", rows));
    }

    @Test
    void refusesAPathWhoseParentIsMissing() {
        var rows = Map.of("a", node("a", "gone", "A"));
        assertThrows(IllegalStateException.class, () -> StandardCatalog.path("a", rows));
    }

    @Test
    void confinesAStandardSetToOneKindWhileADirectoryTakesBoth() {
        var fieldSet = node("set", null, "字段标准组");
        fieldSet.setNodeType(StandardNodeType.STANDARD_SET);
        fieldSet.setStandardKind(StandardKind.FIELD);
        StandardCatalog.requireKind(fieldSet, StandardKind.FIELD);
        var error = assertThrows(BusinessException.class, () -> StandardCatalog.requireKind(fieldSet, StandardKind.LOOKUP));
        assertEquals(CommonErrorCode.PARAM_NOT_VALID, error.getErrorCode());

        var directory = node("dir", null, "公共标准");
        directory.setNodeType(StandardNodeType.DIRECTORY);
        StandardCatalog.requireKind(directory, StandardKind.LOOKUP);
    }

    @Test
    void seedsRootDirectoriesThatCarryNoKind() {
        var root = StandardCatalog.defaultCategory("ws", "actor", "公共标准");
        assertEquals(StandardNodeType.DIRECTORY, root.getNodeType());
        assertNull(root.getStandardKind());
        assertEquals(1, root.getRevision());
        assertTrue(root.getId() != null && !root.getId().isBlank());
    }

    private static CategoryEntity node(String id, String parentId, String name) {
        var row = new CategoryEntity();
        row.setId(id);
        row.setParentId(parentId);
        row.setName(name);
        row.setWorkspaceId("ws");
        row.setNodeType(StandardNodeType.DIRECTORY);
        return row;
    }
}
