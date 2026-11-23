package com.codesprint.lesson0831.agent.evaluation;

import com.codesprint.lesson0831.agent.toolcalling.ModelTurn;
import com.codesprint.lesson0831.agent.toolcalling.ToolCall;

import java.util.List;
import java.util.Objects;

public final class ToolSelectionEvaluation {

    public enum Action {
        SEARCH_PRODUCTS,
        GET_PRODUCT_DETAIL,
        LIST_MY_ORDERS,
        NONE,
        UNKNOWN,
        MULTIPLE
    }

    public enum ErrorType {
        NONE,
        TOOL_OVERUSE,
        TOOL_UNDERUSE,
        WRONG_TOOL
    }

    public record Result(
            boolean correct,
            Action expected,
            Action actual,
            ErrorType errorType
    ) {
        public Result {
            Objects.requireNonNull(
                    expected,
                    "预期动作不能为空"
            );

            Objects.requireNonNull(
                    actual,
                    "实际动作不能为空"
            );

            Objects.requireNonNull(
                    errorType,
                    "错误类型不能为空"
            );
        }
    }

    public Result evaluate(
            Action expected,
            ModelTurn turn
    ) {
        Objects.requireNonNull(
                expected,
                "预期动作不能为空"
        );

        Objects.requireNonNull(
                turn,
                "模型响应不能为空"
        );

        if (expected == Action.UNKNOWN
                || expected == Action.MULTIPLE) {
            throw new IllegalArgumentException(
                    "预期动作只能是三个已注册工具或 NONE"
            );
        }

        Action actual = detectActualAction(turn);

        if (expected == actual) {
            return new Result(
                    true,
                    expected,
                    actual,
                    ErrorType.NONE
            );
        }

        ErrorType errorType;

        if (expected == Action.NONE) {
            errorType = ErrorType.TOOL_OVERUSE;
        } else if (actual == Action.NONE) {
            errorType = ErrorType.TOOL_UNDERUSE;
        } else {
            errorType = ErrorType.WRONG_TOOL;
        }

        return new Result(
                false,
                expected,
                actual,
                errorType
        );
    }

    private Action detectActualAction(
            ModelTurn turn
    ) {
        List<ToolCall> toolCalls =
                turn.toolCalls();

        if (toolCalls.isEmpty()) {
            return Action.NONE;
        }

        if (toolCalls.size() > 1) {
            return Action.MULTIPLE;
        }

        String toolName =
                toolCalls.get(0).name();

        if (toolName == null) {
            return Action.UNKNOWN;
        }

        return switch (toolName) {
            case "search_products" ->
                    Action.SEARCH_PRODUCTS;

            case "get_product_detail" ->
                    Action.GET_PRODUCT_DETAIL;

            case "list_my_orders" ->
                    Action.LIST_MY_ORDERS;

            default ->
                    Action.UNKNOWN;
        };
    }
}