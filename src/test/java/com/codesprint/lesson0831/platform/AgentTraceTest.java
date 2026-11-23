package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.observability.AgentTrace;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentTraceTest {
    @Test void completedAndFailedStepsSurviveSnapshot(){
        var events=new ArrayList<Object>();
        try(var trace=new AgentTrace("trace-test",true,(type,data)->events.add(data))){
            try(var step=AgentTrace.begin("model","模型调用")){step.success("完成");}
            try(var step=AgentTrace.begin("tool","订单查询")){}
            List<?> steps=(List<?>)trace.snapshot().get("steps");
            assertEquals("SUCCESS",((Map<?,?>)steps.get(0)).get("status"));
            assertEquals("FAILED",((Map<?,?>)steps.get(1)).get("status"));
            assertEquals(4,events.size());
        }
        assertFalse(AgentTrace.streaming());
    }
    @Test void unknownUsageAndFirstTokenArePreservedWithoutBreakingAnswer(){
        try(var trace=new AgentTrace(false,null)){
            Map<String,Object> call=new LinkedHashMap<>();call.put("requestId","request");call.put("firstTokenMs",null);call.put("inputTokens",null);
            AgentTrace.gateway(call);AgentTrace.modelStarted();
            assertNull(trace.snapshot().get("inputTokens"));assertEquals(false,trace.snapshot().get("usageComplete"));
            Map<?,?> stored=(Map<?,?>)((List<?>)trace.snapshot().get("gatewayCalls")).get(0);
            assertTrue(stored.containsKey("firstTokenMs"));assertNull(stored.get("firstTokenMs"));
        }
    }
    @Test void nestedGenerationKeepsBusinessTraceAndRestoresScope(){
        try(var root=new AgentTrace("business-trace",true,null)){
            try(var child=new AgentTrace(false,null)){assertEquals("business-trace",AgentTrace.traceId());AgentTrace.modelStarted();AgentTrace.usage(8,3);}
            assertTrue(AgentTrace.streaming());assertEquals(1,root.snapshot().get("modelCalls"));assertEquals(true,root.snapshot().get("usageComplete"));
        }
    }
}
