package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"SHOPGUARD_DB_URL=jdbc:h2:mem:scale_policy;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
    "SHOPGUARD_ADMIN_PASSWORD=policy-fixture-admin","SHOPGUARD_STUDENT_PASSWORD=policy-fixture-student",
    "GATEWAY_API_KEY=policy-test-key","platform.vector-store=local"})
@ActiveProfiles({"prod","platform","gateway"})
class PolicyRevisionTest {
    @Autowired PlatformKnowledgeService library;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockBean PlatformGatewayClient gateway;
    @MockBean PlatformVectorIndex remote;
    @MockBean ChatModel model;
    static Stream<Arguments> cases() throws Exception {
        try(var input=PolicyRevisionTest.class.getResourceAsStream("/scale/policy-cases.json")){
            String[][] rules=new ObjectMapper().readValue(input,String[][].class);
            return Arrays.stream(rules).flatMap(rule->Stream.of("旧版完整","新版完整","新版节选","资料不足")
                .map(mode->Arguments.of(rule[0],mode,rule)));
        }
    }
    @BeforeEach void clear(){
        jdbc.update("DELETE FROM platform_chunks");jdbc.update("DELETE FROM platform_documents");
        when(gateway.embed(anyList())).thenReturn(List.of(List.of(1d,0d)));
    }
    @ParameterizedTest(name="{0} · {1}") @MethodSource("cases")
    void answersKeepTheConditionsOfThisRevision(String title,String mode,String[] rule)throws Exception{
        var old=library.ingest(title,rule[1]);
        var active=old;
        if(!mode.equals("旧版完整")){
            library.delete(old.id());
            active=library.ingest(title,rule[2]);
        }
        String full=mode.equals("旧版完整")?rule[1]:rule[2];
        when(model.chat(any(),anyList())).thenAnswer(call->{
            String prompt=call.<ChatRequest>getArgument(0).question();
            assertTrue(prompt.contains(full));
            var ids=Pattern.compile("chunkId: ([^\\s]+)").matcher(prompt);assertTrue(ids.find());
            if(mode.equals("资料不足"))return "{\"answered\":false,\"answer\":\"\",\"citedChunkIds\":[]}";
            return json.writeValueAsString(Map.of("answered",true,"answer",mode.equals("新版节选")?rule[3]:full,"citedChunkIds",List.of(ids.group(1))));
        });
        var answer=library.answer(mode.equals("资料不足")?"人工客服的电话号码是多少？":rule[4]);
        if(mode.equals("资料不足")){
            assertEquals("INSUFFICIENT_EVIDENCE",answer.code().name());assertTrue(answer.citations().isEmpty());return;
        }
        assertEquals("ANSWERED",answer.code().name());assertTrue(answer.answer().contains(full),"Omitted an eligibility condition");
        assertEquals(1,answer.citations().size());
        var cited=library.source(answer.citations().get(0).chunkId());
        assertEquals(active.version(),cited.get("version"));assertEquals(full,cited.get("text"));
    }
}
