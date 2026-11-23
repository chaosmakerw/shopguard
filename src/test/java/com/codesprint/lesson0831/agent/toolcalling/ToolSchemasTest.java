package com.codesprint.lesson0831.agent.toolcalling;

import com.codesprint.lesson0831.agent.ModelRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolSchemasTest {

    @Test
    void shouldExposeFourReadOnlyTools() {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode request = mapper.valueToTree(
                new ModelRequest(
                        "test-model", List.of(), ToolSchemas.all()
                )
        );

        JsonNode tools = request.get("tools");
        assertEquals(4, tools.size());

        Set<String> names = new HashSet<>();

        for (JsonNode tool : tools) {
            assertEquals("function", tool.get("type").asText());

            JsonNode function = tool.get("function");
            names.add(function.get("name").asText());

            assertEquals(
                    false,
                    function.get("parameters")
                            .get("additionalProperties").booleanValue()
            );
        }

        assertEquals(
                Set.of(
                        "search_products",
                        "get_product_detail",
                        "list_my_orders",
                        "get_my_order"
                ),
                names
        );

        JsonNode orderParameters = tools.get(2)
                .get("function").get("parameters");

        assertEquals(0, orderParameters.get("properties").size());
        assertEquals(0, orderParameters.get("required").size());
        JsonNode detailParameters=tools.get(3).get("function").get("parameters");
        assertEquals("string",detailParameters.path("properties").path("orderId").path("type").asText());
        assertEquals("orderId",detailParameters.path("required").get(0).asText());
    }

    @Test
    void shouldOmitToolsForPlainChatRequest() {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode request = mapper.valueToTree(
                new ModelRequest("test-model", List.of())
        );

        assertFalse(request.has("tools"));
    }
}
