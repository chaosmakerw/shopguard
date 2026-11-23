package com.codesprint.lesson0831.rag.ingestion;

public record IngestionReport(
        int documentCount,
        int chunkCount,
        int created,
        int updated,
        int unchanged,
        int failed
) {
    public IngestionReport {
        if (documentCount < 0
                || chunkCount < 0
                || created < 0
                || updated < 0
                || unchanged < 0
                || failed < 0) {
            throw new IllegalArgumentException(
                    "Ingestion统计不能小于0"
            );
        }

        int total = created
                + updated
                + unchanged
                + failed;

        if (total != chunkCount) {
            throw new IllegalArgumentException(
                    "Chunk总数与处理统计不一致"
            );
        }
    }

    public int total() {
        return created
                + updated
                + unchanged
                + failed;
    }

    public boolean successful() {
        return failed == 0;
    }
}