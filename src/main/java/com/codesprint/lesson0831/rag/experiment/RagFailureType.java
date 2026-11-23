package com.codesprint.lesson0831.rag.experiment;

public enum RagFailureType {
    NONE,
    SOURCE_KNOWLEDGE_MISSING,
    CHUNKING_FAILURE,
    RETRIEVAL_FAILURE,
    GENERATION_FAILURE
}
