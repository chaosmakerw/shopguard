package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetrievalRequestTest {

    @Test
    void shouldCreateRequestAndDefensivelyCopyVisibilities() {
        Set<DocumentVisibility> visibilities =
                new HashSet<>(Set.of(DocumentVisibility.PUBLIC));

        RetrievalRequest request = new RetrievalRequest(
                "退款规则",
                "v1",
                visibilities,
                3,
                0.7
        );

        visibilities.add(DocumentVisibility.ADMIN_ONLY);

        assertEquals("退款规则", request.query());
        assertEquals("v1", request.version());
        assertEquals(Set.of(DocumentVisibility.PUBLIC), request.allowedVisibilities());
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.allowedVisibilities().clear()
        );
    }

    @Test
    void shouldRejectMissingQueryOrVersion() {
        assertThrows(
                NullPointerException.class,
                () -> request(null, "v1", 3, 0.7)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request("   ", "v1", 3, 0.7)
        );
        assertThrows(
                NullPointerException.class,
                () -> request("退款", null, 3, 0.7)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request("退款", "  ", 3, 0.7)
        );
    }

    @Test
    void shouldRejectMissingVisibilityBoundary() {
        assertThrows(
                NullPointerException.class,
                () -> new RetrievalRequest(
                        "退款", "v1", null, 3, 0.7
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetrievalRequest(
                        "退款", "v1", Set.of(), 3, 0.7
                )
        );
    }

    @Test
    void shouldRejectNonPositiveTopK() {
        assertThrows(
                IllegalArgumentException.class,
                () -> request("退款", "v1", 0, 0.7)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request("退款", "v1", -1, 0.7)
        );
    }

    @Test
    void shouldRejectInvalidMinimumScore() {
        assertThrows(
                IllegalArgumentException.class,
                () -> request("退款", "v1", 3, Double.NaN)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request("退款", "v1", 3, Double.POSITIVE_INFINITY)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request("退款", "v1", 3, -1.01)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> request("退款", "v1", 3, 1.01)
        );
    }

    private RetrievalRequest request(
            String query,
            String version,
            int topK,
            double minScore
    ) {
        return new RetrievalRequest(
                query,
                version,
                Set.of(DocumentVisibility.PUBLIC),
                topK,
                minScore
        );
    }
}
