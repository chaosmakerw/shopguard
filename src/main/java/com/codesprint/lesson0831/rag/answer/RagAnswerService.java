package com.codesprint.lesson0831.rag.answer;

import com.codesprint.lesson0831.rag.retrieval.RetrievalCode;
import com.codesprint.lesson0831.rag.retrieval.RetrievalRequest;
import com.codesprint.lesson0831.rag.retrieval.RetrievalResult;
import com.codesprint.lesson0831.rag.retrieval.RetrievalService;
import com.codesprint.lesson0831.rag.retrieval.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RagAnswerService {

    private static final Logger log =
            LoggerFactory.getLogger(RagAnswerService.class);

    private final RetrievalService retrievalService;
    private final RagPromptBuilder promptBuilder;
    private final RagAnswerGenerator answerGenerator;

    public RagAnswerService(
            RetrievalService retrievalService,
            RagPromptBuilder promptBuilder,
            RagAnswerGenerator answerGenerator
    ) {
        this.retrievalService = Objects.requireNonNull(
                retrievalService,
                "检索服务不能为空"
        );
        this.promptBuilder = Objects.requireNonNull(
                promptBuilder,
                "Prompt构造器不能为空"
        );
        this.answerGenerator = Objects.requireNonNull(
                answerGenerator,
                "回答生成器不能为空"
        );
    }

    public RagAnswerResult answer(
            RetrievalRequest request
    ) {
        Objects.requireNonNull(
                request,
                "回答请求不能为空"
        );

        RetrievalResult retrievalResult;

        try {
            retrievalResult =
                    retrievalService.retrieve(request);
        } catch (RuntimeException exception) {
            log.warn(
                    "RAG retrieval failed, exceptionType={}",
                    exception.getClass().getName()
            );
            return RagAnswerResult.retrievalUnavailable();
        }

        if (retrievalResult.code()
                == RetrievalCode.VECTOR_STORE_UNAVAILABLE) {
            return RagAnswerResult.retrievalUnavailable();
        }

        if (retrievalResult.code() == RetrievalCode.NO_RESULT
                || retrievalResult.code()
                == RetrievalCode.LOW_RELEVANCE) {
            return RagAnswerResult.insufficientEvidence();
        }

        RagPrompt prompt = promptBuilder.build(
                request.query(),
                retrievalResult.chunks()
        );

        RagGenerationResult generationResult;

        try {
            generationResult = Objects.requireNonNull(
                    answerGenerator.generate(prompt),
                    "回答生成器不能返回null"
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "RAG generation failed, exceptionType={}",
                    exception.getClass().getName()
            );
            return RagAnswerResult.generationUnavailable();
        }

        if (!generationResult.answered()) {
            return RagAnswerResult.insufficientEvidence();
        }

        List<RagCitation> citations;

        try {
            citations = resolveCitations(
                    retrievalResult.chunks(),
                    generationResult.citedChunkIds()
            );
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "RAG generation returned invalid citations, exceptionType={}",
                    exception.getClass().getName()
            );
            return RagAnswerResult.generationUnavailable();
        }

        return RagAnswerResult.answered(
                generationResult.answer(),
                citations
        );
    }

    private List<RagCitation> resolveCitations(
            List<RetrievedChunk> retrievedChunks,
            List<String> citedChunkIds
    ) {
        Map<String, RetrievedChunk> retrievedById =
                new LinkedHashMap<>();

        for (RetrievedChunk retrievedChunk : retrievedChunks) {
            retrievedById.putIfAbsent(
                    retrievedChunk.chunk().id(),
                    retrievedChunk
            );
        }

        List<RagCitation> citations = new ArrayList<>();

        for (String citedChunkId : citedChunkIds) {
            RetrievedChunk retrievedChunk =
                    retrievedById.get(citedChunkId);

            if (retrievedChunk == null) {
                throw new IllegalArgumentException(
                        "生成结果引用了未检索到的Chunk"
                );
            }

            citations.add(
                    RagCitation.from(retrievedChunk)
            );
        }

        return List.copyOf(citations);
    }
}
