package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring and H2 with the explicitly selected local cosine index; external model is controlled. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:platform-local-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "SHOPGUARD_ADMIN_PASSWORD=LocalTestAdmin!2026","SHOPGUARD_STUDENT_PASSWORD=LocalTestStudent!2026","GATEWAY_API_KEY=local-test-key","GATEWAY_MODEL=campus-chat","platform.vector-store=local"})
@ActiveProfiles({"prod","platform","gateway"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class PlatformLocalKnowledgeTest {
    @Autowired PlatformKnowledgeService knowledge;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    @MockBean PlatformGatewayClient gateway;@MockBean PlatformVectorIndex remote;@MockBean ChatModel model;
    @BeforeEach void setup(){jdbc.update("DELETE FROM platform_chunks");jdbc.update("DELETE FROM platform_documents");
        when(gateway.embed(anyList())).thenAnswer(call->{List<String> texts=call.getArgument(0);return texts.stream().map(text->text.contains("阅读灯")?List.of(0.0,1.0):List.of(1.0,0.0)).toList();});}
    @Test void localIndexUsesRealStoredVectorsAndKeepsOnlyRelevantSource()throws Exception{
        knowledge.ingest("订单取消规则","未付款订单可以取消，库存会归还。");knowledge.ingest("阅读灯说明","阅读灯有三档亮度。");
        when(model.chat(any(ChatRequest.class),anyList())).thenAnswer(call->{
            ChatRequest request=call.getArgument(0);assertTrue(request.question().contains("未付款订单可以取消"));assertFalse(request.question().contains("阅读灯有三档"));
            var match=Pattern.compile("chunkId: ([^\\s]+)").matcher(request.question());assertTrue(match.find());
            return json.writeValueAsString(Map.of("answered",true,"answer","未付款订单可以取消，库存会归还。","citedChunkIds",List.of(match.group(1))));
        });
        var answer=knowledge.answer("订单如何取消？");assertTrue(answer.success());assertEquals(1,answer.citations().size());
        assertEquals("订单取消规则",answer.citations().get(0).source());assertTrue(knowledge.source(answer.citations().get(0).chunkId()).get("text").contains("库存"));verifyNoInteractions(remote);
    }
    @Test void deletingDocumentRemovesItsSearchableSource(){
        var doc=knowledge.ingest("规则","订单取消规则");String chunk=jdbc.queryForObject("SELECT id FROM platform_chunks",String.class);
        knowledge.delete(doc.id());assertTrue(knowledge.documents().isEmpty());assertThrows(PlatformException.class,()->knowledge.source(chunk));verifyNoInteractions(remote);
    }
    @Test void zeroMismatchedAndOppositeVectorsCannotCountAsRelevant()throws Exception{
        assertEquals(-1,PlatformKnowledgeService.cosine(List.of(1.0,0.0),json.readTree("[0,0]")));
        assertEquals(-1,PlatformKnowledgeService.cosine(List.of(1.0,0.0),json.readTree("[1]")));
        assertEquals(-1,PlatformKnowledgeService.cosine(List.of(1.0,0.0),json.readTree("[-1,0]")),1e-9);
    }
}
