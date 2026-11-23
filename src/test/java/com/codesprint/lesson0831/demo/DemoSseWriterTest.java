package com.codesprint.lesson0831.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

/** 纯 Java 单元；真实 JSON 和 OutputStream 替身，无 Spring/HTTP/模型。 */
class DemoSseWriterTest {
    private final ObjectMapper json=new ObjectMapper();
    @Test void unicodeAndLineBreaksRemainInsideJsonFrames() throws Exception {
        var output=new ByteArrayOutputStream();
        DemoSseWriter.write(output,json,answer("😀文本\nevent: forged"));
        String frames=output.toString(StandardCharsets.UTF_8);
        assertThat(frames).contains("event: meta\n", "event: delta\n", "event: done\n");
        assertThat(frames).doesNotContain("\nevent: forged\n");
        for(String frame:frames.split("\n\n")) {
            if(frame.isBlank())continue;
            String data=frame.substring(frame.indexOf("data: ")+6);
            assertThat(json.readTree(data)).isNotNull();
        }
    }
    @Test void brokenConnectionStopsWritingAndDoesNotEmitDone() {
        var bytes=new ByteArrayOutputStream();
        var broken=new OutputStream(){public void write(int b)throws IOException{if(bytes.size()>100)throw new IOException("disconnected");bytes.write(b);}};
        assertThatThrownBy(()->DemoSseWriter.write(broken,json,answer("测试".repeat(50)))).isInstanceOf(IOException.class);
        assertThat(bytes.toString(StandardCharsets.UTF_8)).doesNotContain("event: done");
    }
    @Test void interruptedWriterStopsBeforeDeltaAndCompletion() throws Exception {
        var output=new ByteArrayOutputStream();
        Thread.currentThread().interrupt();
        try{DemoSseWriter.write(output,json,answer("正在接收"));}finally{Thread.interrupted();}
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("event: meta").doesNotContain("event: delta", "event: done");
    }
    private DemoWorkbenchController.WorkbenchAnswer answer(String text){return new DemoWorkbenchController.WorkbenchAnswer(text,"safe-id",1,2,List.of());}
}
