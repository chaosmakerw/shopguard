package com.codesprint.lesson0831.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class CharacterBigramHybridReranker
        implements RetrievalReranker {

    @Override
    public List<RetrievedChunk> rerank(
            String query,
            List<RetrievedChunk> candidates
    ) {
        Objects.requireNonNull(query, "检索问题不能为空");
        Objects.requireNonNull(candidates, "候选片段不能为空");

        Set<String> queryBigrams = bigrams(query);
        List<RetrievedChunk> reranked = new ArrayList<>(candidates.size());

        for (RetrievedChunk candidate : candidates) {
            Objects.requireNonNull(candidate, "候选片段不能包含null");
            Set<String> contentBigrams = bigrams(candidate.chunk().text());
            double lexicalCoverage = coverage(queryBigrams, contentBigrams);
            double hybridScore = 0.5 * candidate.score()
                    + 0.5 * lexicalCoverage;
            reranked.add(new RetrievedChunk(candidate.chunk(), hybridScore));
        }

        reranked.sort(Comparator.comparingDouble(RetrievedChunk::score)
                .reversed()
                .thenComparing(candidate -> candidate.chunk().id()));
        return List.copyOf(reranked);
    }

    private double coverage(Set<String> queryBigrams,
                            Set<String> contentBigrams) {
        if (queryBigrams.isEmpty()) {
            return 0.0;
        }

        int matched = 0;
        for (String bigram : queryBigrams) {
            if (contentBigrams.contains(bigram)) {
                matched++;
            }
        }
        return (double) matched / queryBigrams.size();
    }

    private Set<String> bigrams(String text) {
        StringBuilder normalized = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                normalized.appendCodePoint(Character.toLowerCase(codePoint));
            }
        }

        int[] codePoints = normalized.toString().codePoints().toArray();
        Set<String> result = new HashSet<>();
        for (int index = 0; index + 1 < codePoints.length; index++) {
            result.add(new String(codePoints, index, 2));
        }
        return result;
    }
}
