package com.codesprint.lesson0831.rag.answer;

@FunctionalInterface
public interface RagAnswerGenerator {

    RagGenerationResult generate(RagPrompt prompt);
}