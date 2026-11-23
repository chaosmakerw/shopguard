package com.codesprint.lesson0831.demo;

import com.codesprint.lesson0831.rag.answer.RagAnswerGenerator;
import com.codesprint.lesson0831.rag.answer.RagGenerationResult;
import com.codesprint.lesson0831.rag.answer.RagPrompt;

import java.util.List;
import java.util.regex.Pattern;

/** 直接摘录本次检索的首个片段；没有自然语言推理，也不执行片段中的指令。 */
public final class ExtractiveDemoAnswerGenerator implements RagAnswerGenerator {
    private static final Pattern FIRST_CHUNK = Pattern.compile(
            "chunkId: ([^\\r\\n]+)\\Rsource: [^\\r\\n]+\\Rsection: [^\\r\\n]+\\Rversion: [^\\r\\n]+\\R正文:\\R(.*?)(?=\\n\\n【资料片段|\\z)",
            Pattern.DOTALL);

    @Override
    public RagGenerationResult generate(RagPrompt prompt) {
        var matcher = FIRST_CHUNK.matcher(prompt.context());
        if (!matcher.find()) {
            return RagGenerationResult.noAnswer();
        }
        return RagGenerationResult.answered("知识库原文摘录：\n" + matcher.group(2).strip(),
                List.of(matcher.group(1)));
    }
}
