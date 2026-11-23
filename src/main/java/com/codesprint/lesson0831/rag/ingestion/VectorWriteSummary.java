package com.codesprint.lesson0831.rag.ingestion;

public record VectorWriteSummary(
        int created,
        int updated,
        int unchanged
) {
    public VectorWriteSummary {
        if (created < 0
                || updated < 0
                || unchanged < 0) {
            throw new IllegalArgumentException(
                    "写入统计不能小于0"
            );
        }
    }

    public int total() {
        return created
                + updated
                + unchanged;
    }
}