package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolSelectionMatrixTest {

    @Test
    void shouldProvideThreeCasesForEveryExpectedAction() {
        List<ToolSelectionCase> cases =
                ToolSelectionMatrix.standardCases();

        assertEquals(12, cases.size());

        Map<Action, Long> countsByAction =
                cases.stream()
                        .collect(
                                Collectors.groupingBy(
                                        ToolSelectionCase::expectedAction,
                                        Collectors.counting()
                                )
                        );

        assertEquals(3L, countsByAction.get(Action.SEARCH_PRODUCTS));
        assertEquals(3L, countsByAction.get(Action.GET_PRODUCT_DETAIL));
        assertEquals(3L, countsByAction.get(Action.LIST_MY_ORDERS));
        assertEquals(3L, countsByAction.get(Action.NONE));
    }

    @Test
    void shouldKeepCaseIdsUniqueAndDescriptionsReadable() {
        List<ToolSelectionCase> cases =
                ToolSelectionMatrix.standardCases();

        Set<String> ids = new HashSet<>();

        for (ToolSelectionCase testCase : cases) {
            assertFalse(testCase.id().isBlank());
            assertFalse(testCase.userMessage().isBlank());
            assertFalse(testCase.expectationReason().isBlank());
            ids.add(testCase.id());
        }

        assertEquals(cases.size(), ids.size());
    }

    @Test
    void shouldIncludeNoToolBoundaryCase() {
        List<ToolSelectionCase> cases =
                ToolSelectionMatrix.standardCases();

        ToolSelectionCase boundaryCase =
                cases.stream()
                        .filter(testCase ->
                                testCase.id().equals("none-tool-name"))
                        .findFirst()
                        .orElseThrow();

        assertEquals(Action.NONE, boundaryCase.expectedAction());
        assertEquals(
                "只是讨论工具名称，不应执行商品业务工具",
                boundaryCase.expectationReason()
        );
    }

    @Test
    void shouldRejectInvalidCaseDefinitions() {
        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionCase(
                        null,
                        "搜索 Java 书",
                        Action.SEARCH_PRODUCTS,
                        "需要搜索商品"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionCase(
                        " ",
                        "搜索 Java 书",
                        Action.SEARCH_PRODUCTS,
                        "需要搜索商品"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionCase(
                        "search-empty-message",
                        " ",
                        Action.SEARCH_PRODUCTS,
                        "需要搜索商品"
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> new ToolSelectionCase(
                        "search-null-action",
                        "搜索 Java 书",
                        null,
                        "需要搜索商品"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionCase(
                        "unknown-action",
                        "随便调用一个未知工具",
                        Action.UNKNOWN,
                        "非法预期动作"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionCase(
                        "multiple-action",
                        "同时调用多个工具",
                        Action.MULTIPLE,
                        "非法预期动作"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSelectionCase(
                        "search-empty-reason",
                        "搜索 Java 书",
                        Action.SEARCH_PRODUCTS,
                        " "
                )
        );
    }

    @Test
    void shouldExposeReadOnlyStandardCases() {
        List<ToolSelectionCase> cases =
                ToolSelectionMatrix.standardCases();

        assertThrows(
                UnsupportedOperationException.class,
                () -> cases.add(
                        new ToolSelectionCase(
                                "extra-case",
                                "额外问题",
                                Action.NONE,
                                "验证列表只读"
                        )
                )
        );
    }
}
