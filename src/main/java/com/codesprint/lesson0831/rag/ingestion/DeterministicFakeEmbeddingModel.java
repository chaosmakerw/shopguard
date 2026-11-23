package com.codesprint.lesson0831.rag.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DeterministicFakeEmbeddingModel
        implements TextEmbeddingModel {

    private final int dimensions;

    public DeterministicFakeEmbeddingModel(
            int dimensions
    ) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException(
                    "向量维度必须大于0"
            );
        }

        this.dimensions = dimensions;
    }

    @Override
    public List<Double> embed(
            String text
    ) {
        Objects.requireNonNull(
                text,
                "待向量化文本不能为空"
        );

        if (text.isBlank()) {
            throw new IllegalArgumentException(
                    "待向量化文本不能为空"
            );
        }

        double[] values =
                new double[dimensions];

        text.codePoints().forEach(codePoint -> {
            int bucket = Math.floorMod(
                    codePoint,
                    dimensions
            );

            values[bucket] =
                    values[bucket] + 1.0;
        });

        double squaredSum = 0.0;

        for (double value : values) {
            squaredSum =
                    squaredSum + value * value;
        }

        double length = Math.sqrt(
                squaredSum
        );

        List<Double> normalizedVector =
                new ArrayList<>(dimensions);

        for (double value : values) {
            normalizedVector.add(
                    value / length
            );
        }

        return List.copyOf(normalizedVector);
    }
}