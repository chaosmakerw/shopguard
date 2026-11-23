package com.codesprint.lesson0831.platform;

import com.codesprint.lesson0831.agent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@Profile("platform")
@RequestMapping("/api/platform")
public class PlatformController {
    private final PlatformRepository repository;private final ConversationMemory memory;private final PlatformChatService chat;
    private final PlatformGatewayClient gateway;private final PlatformVectorIndex vectors;private final Environment env;private final ObjectMapper json;
    public PlatformController(PlatformRepository repository,ConversationMemory memory,PlatformChatService chat,PlatformGatewayClient gateway,PlatformVectorIndex vectors,Environment env,ObjectMapper json){
        this.repository=repository;this.memory=memory;this.chat=chat;this.gateway=gateway;this.vectors=vectors;this.env=env;this.json=json;
    }
    public record SessionInput(@NotNull @Pattern(regexp="business|knowledge") String mode){}
    public record ChatInput(@NotBlank @Size(max=100) String sessionId,@NotBlank @Size(max=4000) String question,@Pattern(regexp="[1-9][0-9]{0,18}") String orderId){}
    @org.springframework.beans.factory.annotation.Autowired private com.codesprint.lesson0831.trade.core.JdbcTradeService trades;
    private void validateOrderContext(PlatformIdentity user,ChatInput input){
        if(input.orderId()==null)return;
        long id;
        try{id=Long.parseLong(input.orderId());}catch(NumberFormatException invalid){throw new PlatformException(400,"INVALID_ORDER_ID","订单编号无效");}
        try{trades.order(user.id(),id);}
        catch(com.codesprint.lesson0831.trade.core.TradeException denied){throw new PlatformException(denied.httpStatus(),denied.code(),denied.getMessage());}
    }
    @GetMapping("/sessions") public Object sessions(@AuthenticationPrincipal PlatformIdentity user){return repository.sessions(user.id());}
    @PostMapping("/sessions") public Object create(@AuthenticationPrincipal PlatformIdentity user,@Valid @RequestBody SessionInput input){return Map.of("id",repository.createSession(user.id(),input.mode()));}
    @GetMapping("/sessions/{id}") public Object messages(@AuthenticationPrincipal PlatformIdentity user,@PathVariable String id){
        String mode=repository.sessionMode(user.id(),id);
        return Map.of("mode",mode,"messages",memory.load(new ConversationKey(Long.toString(user.id()),id)),"details",repository.sessionDetails(user.id(),id));
    }
    @DeleteMapping("/sessions/{id}") public Object delete(@AuthenticationPrincipal PlatformIdentity user,@PathVariable String id){chat.delete(user.id(),id);return Map.of("success",true);}
    @PostMapping("/chat") public Object chat(@AuthenticationPrincipal PlatformIdentity user,@Valid @RequestBody ChatInput input){
        validateOrderContext(user,input);
        return chat.answer(user.id(),input.sessionId(),input.question(),false,null,input.orderId());
    }
    @PostMapping(value="/chat/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@AuthenticationPrincipal PlatformIdentity user,@Valid @RequestBody ChatInput input,HttpServletResponse response){
        repository.sessionMode(user.id(),input.sessionId());
        validateOrderContext(user,input);
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        boolean streaming="openai".equals(env.getProperty("platform.model-mode"));
        return ResponseEntity.ok().header("Cache-Control","no-store").contentType(MediaType.TEXT_EVENT_STREAM).body(output->{
            var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);SecurityContextHolder.setContext(context);
            try {
                send(output,response,"phase",Map.of("label","正在处理请求"));
                var result=chat.answer(user.id(),input.sessionId(),input.question(),streaming,(type,data)->{
                    try{send(output,response,type,data);}catch(IOException e){throw new java.io.UncheckedIOException(e);}
                },input.orderId());
                send(output,response,"done",result);
            }catch(Exception e){try{send(output,response,"error",Map.of("code",e instanceof PlatformException p?p.code():"MODEL_UNAVAILABLE",
                "message",e instanceof PlatformException p?p.getMessage():"本次回答未完成，请检查模型配置或稍后重试"));}catch(IOException disconnected){}}
            finally{SecurityContextHolder.setContext(previous);}
        });
    }
    @GetMapping("/runs") public Object runs(@AuthenticationPrincipal PlatformIdentity user){return repository.runs(user.id(),"ADMIN".equals(user.role()));}
    @GetMapping("/admin/gateway/requests") public Object gatewayRequests(){return gateway.request("/api/gateway/platform/observations",null);}
    @GetMapping("/settings") public Object settings(){
        return Map.of("mode",env.getProperty("platform.model-mode","openai"),"model",env.getProperty("GATEWAY_MODEL","openai-chat"),
            "embeddingModel",env.getProperty("platform.embedding-model","text-embedding-3-small"),"sessionMinutes",30,"knowledgeStore",env.getProperty("platform.vector-store","qdrant").equals("local")?"本地向量检索":"Qdrant","knowledgeScope","所有已登录用户共享的平台规则");
    }
    @GetMapping("/admin/health") public Object health(){
        Map<String,Object> result=new LinkedHashMap<>();
        try{result.put("gateway",gateway.request("/api/gateway/platform/status",null));}catch(RuntimeException e){result.put("gateway",Map.of("available",false));}
        result.put("vectorStore",env.getProperty("platform.vector-store","qdrant").equals("local")||vectors.healthy());return result;
    }
    private void send(OutputStream output,HttpServletResponse response,String event,Object data)throws IOException{
        output.write(("event: "+event+"\ndata: "+json.writeValueAsString(data)+"\n\n").getBytes(StandardCharsets.UTF_8));output.flush();response.flushBuffer();
    }
}
