package com.codesprint.lesson0831.rag.ingestion;

import java.util.List;

@FunctionalInterface
public interface TextEmbeddingModel {

    List<Double> embed(String text);
}
