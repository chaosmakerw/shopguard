package com.codesprint.lesson0831.rag.retrieval;

import com.codesprint.lesson0831.rag.ingestion.DocumentVisibility;
import com.codesprint.lesson0831.security.CurrentUserContext;

import java.util.Objects;
import java.util.Set;

public final class TrustedRetrievalRequestFactory {

    private final CurrentUserContext currentUserContext;

    public TrustedRetrievalRequestFactory(
            CurrentUserContext currentUserContext
    ) {
        this.currentUserContext = Objects.requireNonNull(
                currentUserContext,
                "当前用户上下文不能为空"
        );
    }

    public RetrievalRequest create(
            String query,
            String version,
            int topK,
            double minScore
    ) {
        Long userId = currentUserContext.getUserId();
        Set<DocumentVisibility> allowedVisibilities;

        if (userId == null || userId <= 0) {
            allowedVisibilities = Set.of(DocumentVisibility.PUBLIC);
        } else if (currentUserContext.isAdmin()) {
            allowedVisibilities = Set.of(
                    DocumentVisibility.PUBLIC,
                    DocumentVisibility.AUTHENTICATED,
                    DocumentVisibility.ADMIN_ONLY
            );
        } else {
            allowedVisibilities = Set.of(
                    DocumentVisibility.PUBLIC,
                    DocumentVisibility.AUTHENTICATED
            );
        }

        return new RetrievalRequest(
                query,
                version,
                allowedVisibilities,
                topK,
                minScore
        );
    }
}