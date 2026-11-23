package com.codesprint.lesson0831.rag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeIngestionServiceTest {

    @Test
    void shouldRunCompletePipelineAndCreateAllChunks() {
        InMemoryVectorStore store =
                new InMemoryVectorStore(8);
        KnowledgeIngestionService service =
                createService(store);

        IngestionReport report = service.ingest(
                KnowledgeDocumentCatalog.standardMetadata()
        );

        assertEquals(4, report.documentCount());
        assertTrue(report.chunkCount() > 4);
        assertEquals(report.chunkCount(), report.created());
        assertEquals(0, report.updated());
        assertEquals(0, report.unchanged());
        assertEquals(0, report.failed());
        assertTrue(report.successful());
        assertEquals(report.chunkCount(), store.size());
    }

    @Test
    void shouldReportAllChunksUnchangedWhenSameDocumentsAreIngestedAgain() {
        InMemoryVectorStore store =
                new InMemoryVectorStore(8);
        KnowledgeIngestionService service =
                createService(store);

        IngestionReport firstReport = service.ingest(
                KnowledgeDocumentCatalog.standardMetadata()
        );
        int sizeAfterFirstRun = store.size();

        IngestionReport secondReport = service.ingest(
                KnowledgeDocumentCatalog.standardMetadata()
        );

        assertEquals(0, secondReport.created());
        assertEquals(0, secondReport.updated());
        assertEquals(
                firstReport.chunkCount(),
                secondReport.unchanged()
        );
        assertEquals(
                sizeAfterFirstRun,
                store.size()
        );
    }

    @Test
    void shouldKeepSourceVersionAndVisibilityInStoredChunks() {
        InMemoryVectorStore store =
                new InMemoryVectorStore(8);
        KnowledgeIngestionService service =
                createService(store);

        service.ingest(
                KnowledgeDocumentCatalog.standardMetadata()
        );

        assertTrue(store.findAll().stream()
                .anyMatch(embedded ->
                        embedded.chunk()
                                .metadata()
                                .source()
                                .equals(
                                        "knowledge/security-guide.md"
                                )
                                && embedded.chunk()
                                .metadata()
                                .version()
                                .equals("2026-09-14")
                                && embedded.chunk()
                                .metadata()
                                .visibility()
                                == DocumentVisibility.ADMIN_ONLY));
    }

    @Test
    void shouldRejectMissingOrEmptyMetadataBeforePipelineStarts() {
        KnowledgeIngestionService service =
                createService(
                        new InMemoryVectorStore(8)
                );

        assertThrows(
                NullPointerException.class,
                () -> service.ingest(null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.ingest(List.of())
        );
    }

    @Test
    void shouldValidateIngestionReportCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IngestionReport(
                        -1,
                        0,
                        0,
                        0,
                        0,
                        0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new IngestionReport(
                        1,
                        3,
                        1,
                        1,
                        0,
                        0
                )
        );

        IngestionReport failedReport =
                new IngestionReport(
                        1,
                        2,
                        1,
                        0,
                        0,
                        1
                );

        assertEquals(2, failedReport.total());
        assertTrue(!failedReport.successful());
    }

    private KnowledgeIngestionService createService(
            VectorStore store
    ) {
        return new KnowledgeIngestionService(
                new ClasspathKnowledgeDocumentLoader(),
                new KnowledgeDocumentPreprocessor(),
                new KnowledgeDocumentChunker(80, 20),
                new KnowledgeChunkEmbedder(
                        new DeterministicFakeEmbeddingModel(8)
                ),
                store
        );
    }
    @Test
    void shouldRebuildIdenticalContentsFromTwoEmptyStores() {
        // 1. 创建两个独立的空向量库。
        InMemoryVectorStore firstStore =
                new InMemoryVectorStore(8);
        InMemoryVectorStore secondStore =
                new InMemoryVectorStore(8);

        assertEquals(0, firstStore.size());
        assertEquals(0, secondStore.size());

        // 2. 为两个库分别创建导入服务。
        KnowledgeIngestionService firstService =
                createService(firstStore);
        KnowledgeIngestionService secondService =
                createService(secondStore);

        // 3. 向第一个空库导入标准文档。
        IngestionReport firstReport = firstService.ingest(
                KnowledgeDocumentCatalog.standardMetadata()
        );

        // 第一个库的导入不能影响第二个库。
        assertEquals(0, secondStore.size());

        // 4. 向第二个空库导入相同文档。
        IngestionReport secondReport = secondService.ingest(
                KnowledgeDocumentCatalog.standardMetadata()
        );

        // 5. 第一个空库应成功导入全部片段。
        assertEquals(4, firstReport.documentCount());
        assertTrue(firstReport.chunkCount() > 0);
        assertEquals(firstReport.chunkCount(), firstReport.created());
        assertEquals(0, firstReport.updated());
        assertEquals(0, firstReport.unchanged());
        assertEquals(0, firstReport.failed());
        assertEquals(firstReport.chunkCount(), firstStore.size());

        // 6. 第二个库也是首次导入，因此全部片段仍应为新增。
        assertEquals(secondReport.chunkCount(), secondReport.created());
        assertEquals(0, secondReport.updated());
        assertEquals(0, secondReport.unchanged());
        assertEquals(0, secondReport.failed());
        assertEquals(secondReport.chunkCount(), secondStore.size());

        // 7. 比较两次导入的统计结果。
        assertEquals(
                firstReport,
                secondReport,
                "相同文档与配置从空库重建，应得到相同导入报告"
        );

        // 8. 比较实际保存的数据，不能只比较数量。
        assertEquals(
                firstStore.findAll(),
                secondStore.findAll(),
                "两次重建的片段、元数据、向量及排列顺序应一致"
        );
    }
}
