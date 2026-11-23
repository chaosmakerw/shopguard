package com.codesprint.lesson0831.agent;

import com.codesprint.lesson0831.agent.toolcalling.TimeBudget;
import com.codesprint.lesson0831.observability.AgentTrace;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;

/** Consumes the Gateway's validated stream events, including assembled tool calls. */
final class GatewayStreamTransport {
    private final URI endpoint; private final String key; private final HttpClient client;
    private final ObjectMapper json=new ObjectMapper();
    GatewayStreamTransport(URI chatEndpoint,String key,HttpClient client) {
        this.endpoint=chatEndpoint.resolve("/api/gateway/platform/stream"); this.key=key; this.client=client;
    }
    ModelResponse send(ModelRequest request,TimeBudget budget) {
        ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"shopguard-stream-deadline");t.setDaemon(true);return t; });
        try {
            var httpRequest=HttpRequest.newBuilder(endpoint).timeout(Duration.ofMillis(budget.remainingMillis()))
                .header("Content-Type","application/json").header("X-API-Key",key)
                .header("X-Trace-Id",AgentTrace.traceId())
                .header("X-Idempotency-Key",UUID.randomUUID().toString()).header("X-Request-Timeout-Ms",Long.toString(budget.remainingMillis()))
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request),StandardCharsets.UTF_8)).build();
            var response=client.send(httpRequest,HttpResponse.BodyHandlers.ofInputStream());
            try(InputStream input=response.body()) {
                if(response.statusCode()!=200 || !response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream"))
                    throw new ModelCallException("网关流式请求失败");
                var deadline=timer.schedule(() -> {try{input.close();}catch(IOException ignored){}},Math.max(1,budget.remainingMillis()),TimeUnit.MILLISECONDS);
                try {
                    var message=json.createObjectNode(); message.put("role","assistant");
                    StringBuilder text=new StringBuilder(); boolean done=false; String event="message";
                    try(var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
                        String line; int total=0;
                        while((line=reader.readLine())!=null) {
                            total+=line.length(); if(line.length()>262144 || total>1048576) throw new IOException("STREAM_LIMIT");
                            budget.checkNotExpired();
                            if(line.startsWith("event:")) event=line.substring(6).trim();
                            if(!line.startsWith("data:")) continue;
                            JsonNode data=json.readTree(line.substring(5).trim());
                            switch(event) {
                                case "text" -> {String part=data.path("text").asText();text.append(part);AgentTrace.event("delta",part);}
                                case "tools" -> {var calls=message.putArray("tool_calls"); for(JsonNode call:data) {
                                    var item=calls.addObject();item.put("id",call.path("id").asText());item.put("type","function");
                                    item.putObject("function").put("name",call.path("name").asText()).put("arguments",call.path("argumentsJson").asText());
                                }}
                                case "usage" -> AgentTrace.usage(data.path("inputTokens").asLong(-1),data.path("outputTokens").asLong(-1));
                                case "observation" -> AgentTrace.gateway(json.convertValue(data,new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>(){}));
                                case "error" -> throw new IOException("UPSTREAM_STREAM_FAILED");
                                case "done" -> done=true;
                            }
                            if(done) break;
                        }
                    }
                    if(!done) throw new IOException("INCOMPLETE_STREAM");
                    message.put("content",text.toString());
                    var result=json.createObjectNode(); result.putArray("choices").addObject().set("message",message);
                    return json.readerFor(ModelResponse.class).without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(result);
                } finally {deadline.cancel(false);}
            }
        } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new ModelCallException("流式请求已中断",e);}
        catch(IOException e) {throw new ModelCallException("流式请求未完成",e);}
        finally {timer.shutdownNow();}
    }
}
