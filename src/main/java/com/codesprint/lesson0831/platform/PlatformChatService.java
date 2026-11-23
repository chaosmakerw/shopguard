package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.*;
import com.codesprint.lesson0831.observability.AgentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

@Service
@Profile("platform")
public class PlatformChatService {
    private static final ReentrantLock[] LOCKS=new ReentrantLock[64];
    static {for(int i=0;i<LOCKS.length;i++)LOCKS[i]=new ReentrantLock();}
    private final PlatformRepository repository;private final ConversationMemory memory;
    private final PlatformKnowledgeService knowledge;private final ObjectMapper json;
    private final SupportScopePolicy scope;private final SupportAgentService support;
    public PlatformChatService(PlatformRepository repository,ConversationMemory memory,PlatformKnowledgeService knowledge,ObjectMapper json,SupportScopePolicy scope,SupportAgentService support){
        this.repository=repository;this.memory=memory;this.knowledge=knowledge;this.json=json;this.scope=scope;this.support=support;
    }
    private ReentrantLock lock(long user,String session){return LOCKS[Math.floorMod(Objects.hash(user,session),LOCKS.length)];}
    public Map<String,Object> answer(long user,String session,String question,boolean streaming,BiConsumer<String,Object> events) {
        return answer(user,session,question,streaming,events,null);
    }
    public Map<String,Object> answer(long user,String session,String question,boolean streaming,BiConsumer<String,Object> events,String orderId) {
        var lock=lock(user,session);
        if(!lock.tryLock())throw new PlatformException(409,"SESSION_BUSY","这个会话正在处理上一条消息，请稍后再试");
        String mode;
        try {mode=repository.sessionMode(user,session);}catch(RuntimeException failure){lock.unlock();throw failure;}
        String id=UUID.randomUUID().toString();long started=System.nanoTime();
        String code="FAILED";List<?> citations=List.of();long sequence=0;Map<String,Object> supportContext=Map.of();
        try(AgentTrace trace=new AgentTrace(id,streaming,events)) {
            AgentTrace.event("trace",Map.of("traceId",id));
            try {
                String answer;
                var key=new ConversationKey(Long.toString(user),session);
                List<ChatMessage> history=memory.load(key);
                Map<String,Object> previous=context(user,session,history);
                var decision=scope.decide(question,String.valueOf(previous.getOrDefault("topic","")),orderId!=null);
                try(var check=AgentTrace.begin("scope","客服服务范围检查")) {
                    if(decision.local() && "OUT_OF_SCOPE".equals(decision.code()))check.rejected("问题超出商品、本人订单、售后与平台规则范围；未调用模型");
                    else check.success(decision.local()?"由平台客服规则直接说明":"已进入允许的业务范围");
                }
                if(decision.local()) { answer=decision.answer();code=decision.code(); }
                else if(decision.topic()==SupportScopePolicy.Topic.RULES || "knowledge".equals(mode)) {
                    AgentTrace.event("phase",Map.of("label","正在检索平台规则"));
                    String query=question;
                    if(scope.followsPrevious(question) && !history.isEmpty() && "RULES".equals(previous.get("topic"))) query="上次平台规则问题："+history.get(Math.max(0,history.size()-2)).content()+"\n本次追问："+question;
                    var result=knowledge.answer(query);code=result.code().name();
                    if(!result.success())throw new PlatformException(503,code,result.answer());
                    answer=result.answer();citations=result.citations();
                    if(!citations.isEmpty())supportContext=Map.of("topic","RULES");
                }else {
                    Map<String,Object> selected=new LinkedHashMap<>();
                    if(scope.followsPrevious(question) && decision.topic().name().equals(previous.get("topic")))selected.putAll(previous);
                    if(!scope.refersToSameEntity(question)){selected.remove("orderId");selected.remove("productId");}
                    if(orderId!=null && decision.topic()==SupportScopePolicy.Topic.ORDERS)selected.put("orderId",orderId);
                    var result=support.answer(user,session,question,decision.topic(),selected,history,events,streaming);
                    answer=result.text();code=result.code();supportContext=result.context();
                }
                memory.appendTurn(key,question,answer);
                sequence=memory.load(new ConversationKey(Long.toString(user),session)).stream().mapToLong(ChatMessage::sequence).max().orElse(0);
                repository.touchSession(user,session,question);
                Map<String,Object> result=new LinkedHashMap<>();result.put("answer",answer);result.put("citations",citations);result.put("requestId",id);result.put("code",code);result.put("trace",trace.snapshot());
                result.put("support",supportContext);
                if(orderId!=null)result.put("orderId",orderId);
                return result;
            } finally {
                Map<String,Object> detail=new LinkedHashMap<>(trace.snapshot());detail.put("citations",citations);detail.put("sequence",sequence);detail.put("support",supportContext);
                if(orderId!=null)detail.put("orderId",orderId);
                repository.recordRun(id,user,session,mode,code,(System.nanoTime()-started)/1_000_000,encode(detail));
            }
        } finally {lock.unlock();}
    }
    private Map<String,Object> context(long user,String session,List<ChatMessage> history) {
        var last=repository.latestRun(user,session);
        if(last.isEmpty() || !Set.of("SUCCESS","ANSWERED").contains(last.get().code()))return Map.of();
        try {
            var detail=json.readTree(last.get().details());
            long latest=history.stream().mapToLong(ChatMessage::sequence).max().orElse(0);
            if(detail.path("sequence").asLong(-1)!=latest)return Map.of();
            var previous=detail.path("support");String topic=previous.path("topic").asText();
            if(!Set.of("PRODUCTS","ORDERS","RULES").contains(topic))return Map.of();
            Map<String,Object> result=new LinkedHashMap<>();result.put("topic",topic);
            for(String key:List.of("orderId","productId")){String value=previous.path(key).asText();if(value.matches("[1-9][0-9]{0,18}"))result.put(key,value);}
            return result;
        }catch(java.io.IOException invalid){return Map.of();}
    }
    public void delete(long user,String session){var lock=lock(user,session);if(!lock.tryLock())throw new PlatformException(409,"SESSION_BUSY","请等待当前回答结束后再删除");try{repository.deleteSession(user,session);}finally{lock.unlock();}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
