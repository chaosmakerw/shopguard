package com.codesprint.lesson0831.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 已完成结果通过真实 SSE 传输；不是供应商 Token 流。断开时直接抛出 IOException。 */
public final class DemoSseWriter {
    private DemoSseWriter() {}
    public static void write(OutputStream output, ObjectMapper json,
                             DemoWorkbenchController.WorkbenchAnswer answer) throws IOException {
        event(output, json, "meta", Map.of("transport", "COMPLETION_REPLAY", "requestId", answer.requestId()));
        String text = answer.answer();
        for (int start = 0; start < text.length();) {
            if (Thread.currentThread().isInterrupted()) return;
            int end = text.offsetByCodePoints(start, Math.min(16, text.codePointCount(start, text.length())));
            event(output, json, "delta", Map.of("text", text.substring(start, end)));
            start = end;
        }
        event(output, json, "done", answer);
    }
    public static void event(OutputStream output, ObjectMapper json, String name, Object data) throws IOException {
        String frame = "event: " + name + "\ndata: " + json.writeValueAsString(data) + "\n\n";
        output.write(frame.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
}
