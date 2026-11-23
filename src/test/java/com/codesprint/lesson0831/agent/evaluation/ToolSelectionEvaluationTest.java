package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Action;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.ErrorType;
import com.codesprint.lesson0831.agent.evaluation.ToolSelectionEvaluation.Result;
import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSelectionEvaluationTest {

    private final ToolSelectionEvaluation evaluation =
            new ToolSelectionEvaluation();

    @Test
    void shouldAcceptNoneWhenModelReturnsTextWithoutToolCall() {
        Result result = evaluation.evaluate(
                Action.NONE,
                new ModelTurn(
                        "这是普通知识回答",
                        List.of()
                )
        );

        assertCorrect(
                result,
                Action.NONE
        );
    }

    @Test
    void shouldAcceptSearchProductsWhenSearchToolIsSelected() {
        Result result = evaluation.evaluate(
                Action.SEARCH_PRODUCTS,
                toolTurn("search_products")
        );

        assertCorrect(
                result,
                Action.SEARCH_PRODUCTS
        );
    }

    @Test
    void shouldAcceptProductDetailWhenDetailToolIsSelected() {
        Result result = evaluation.evaluate(
                Action.GET_PRODUCT_DETAIL,
                toolTurn("get_product_detail")
        );

        assertCorrect(
                result,
                Action.GET_PRODUCT_DETAIL
        );
    }

    @Test
    void shouldAcceptMyOrdersWhenOrderToolIsSelected() {
        Result result = evaluation.evaluate(
                Action.LIST_MY_ORDERS,
                toolTurn("list_my_orders")
        );

        assertCorrect(
                result,
                Action.LIST_MY_ORDERS
        );
    }

    @Test
    void shouldClassifyUnexpectedToolCallAsToolOveruse() {
        Result result = evaluation.evaluate(
                Action.NONE,
                toolTurn("search_products")
        );

        assertIncorrect(
                result,
                Action.NONE,
                Action.SEARCH_PRODUCTS,
                ErrorType.TOOL_OVERUSE
        );
    }

    @Test
    void shouldClassifyMissingToolCallAsToolUnderuse() {
        Result result = evaluation.evaluate(
                Action.GET_PRODUCT_DETAIL,
                new ModelTurn(
                        "我直接猜测商品详情",
                        List.of()
                )
        );

        assertIncorrect(
                result,
                Action.GET_PRODUCT_DETAIL,
                Action.NONE,
                ErrorType.TOOL_UNDERUSE
        );
    }

    @Test
    void shouldClassifyDifferentKnownToolAsWrongTool() {
        Result result = evaluation.evaluate(
                Action.SEARCH_PRODUCTS,
                toolTurn("get_product_detail")
        );

        assertIncorrect(
                result,
                Action.SEARCH_PRODUCTS,
                Action.GET_PRODUCT_DETAIL,
                ErrorType.WRONG_TOOL
        );
    }

    @Test
    void shouldClassifyUnknownToolNameAsWrongTool() {
        Result result = evaluation.evaluate(
                Action.LIST_MY_ORDERS,
                toolTurn("delete_order")
        );

        assertIncorrect(
                result,
                Action.LIST_MY_ORDERS,
                Action.UNKNOWN,
                ErrorType.WRONG_TOOL
        );
    }

    @Test
    void shouldClassifyMultipleToolCallsAsWrongTool() {
        ModelTurn turn = new ModelTurn(
                null,
                List.of(
                        toolCall(
                                "call-1",
                                "search_products"
                        ),
                        toolCall(
                                "call-2",
                                "get_product_detail"
                        )
                )
        );

        Result result = evaluation.evaluate(
                Action.SEARCH_PRODUCTS,
                turn
        );

        assertIncorrect(
                result,
                Action.SEARCH_PRODUCTS,
                Action.MULTIPLE,
                ErrorType.WRONG_TOOL
        );
    }

    @Test
    void shouldClassifyMultipleCallsAsOveruseWhenNoToolWasExpected() {
        ModelTurn turn = new ModelTurn(
                null,
                List.of(
                        toolCall(
                                "call-1",
                                "search_products"
                        ),
                        toolCall(
                                "call-2",
                                "list_my_orders"
                        )
                )
        );

        Result result = evaluation.evaluate(
                Action.NONE,
                turn
        );

        assertIncorrect(
                result,
                Action.NONE,
                Action.MULTIPLE,
                ErrorType.TOOL_OVERUSE
        );
    }

    @Test
    void shouldRejectActualOnlyActionAsExpectedAction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluation.evaluate(
                        Action.UNKNOWN,
                        toolTurn("search_products")
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> evaluation.evaluate(
                        Action.MULTIPLE,
                        toolTurn("search_products")
                )
        );
    }

    @Test
    void shouldRejectNullExpectedActionOrModelTurn() {
        assertThrows(
                NullPointerException.class,
                () -> evaluation.evaluate(
                        null,
                        toolTurn("search_products")
                )
        );

        assertThrows(
                NullPointerException.class,
                () -> evaluation.evaluate(
                        Action.SEARCH_PRODUCTS,
                        null
                )
        );
    }

    private ModelTurn toolTurn(String toolName) {
        return new ModelTurn(
                null,
                List.of(
                        toolCall(
                                "call-1",
                                toolName
                        )
                )
        );
    }

    private ToolCall toolCall(
            String callId,
            String toolName
    ) {
        return new ToolCall(
                callId,
                toolName,
                Map.of()
        );
    }

    private void assertCorrect(
            Result result,
            Action expected
    ) {
        assertTrue(result.correct());
        assertEquals(expected, result.expected());
        assertEquals(expected, result.actual());
        assertEquals(
                ErrorType.NONE,
                result.errorType()
        );
    }

    private void assertIncorrect(
            Result result,
            Action expected,
            Action actual,
            ErrorType errorType
    ) {
        assertFalse(result.correct());
        assertEquals(expected, result.expected());
        assertEquals(actual, result.actual());
        assertEquals(errorType, result.errorType());
    }
}
