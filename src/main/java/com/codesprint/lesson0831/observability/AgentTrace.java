package com.codesprint.lesson0831.observability;

import java.util.*;
import java.util.function.BiConsumer;

/** Request-local operational events; never records prompts, arguments, credentials or tool results. */
public final class AgentTrace implements AutoCloseable {
    private static final ThreadLocal<AgentTrace> CURRENT=new ThreadLocal<>();
    private final AgentTrace previous; private final BiConsumer<String,Object> sink;
    private final boolean streaming; private final List<String> tools=new ArrayList<>();
    private int modelCalls; private long inputTokens,outputTokens; private int reportedCalls;
    private final String traceId;
    private final long started;
    private final List<Map<String,Object>> steps=new ArrayList<>();
    private final List<Map<String,Object>> gatewayCalls=new ArrayList<>();
    public AgentTrace(boolean streaming,BiConsumer<String,Object> sink) {
        this(CURRENT.get()==null?UUID.randomUUID().toString():CURRENT.get().traceId,streaming,sink);
    }
    public AgentTrace(String traceId,boolean streaming,BiConsumer<String,Object> sink) {
        this.previous=CURRENT.get(); this.streaming=streaming; this.sink=sink; CURRENT.set(this);
        this.traceId=traceId;
        this.started=previous==null?System.nanoTime():previous.started;
    }
    public static String traceId(){return CURRENT.get()==null?UUID.randomUUID().toString():CURRENT.get().traceId;}
    public static Step begin(String kind,String label){return new Step(CURRENT.get(),kind,label);}
    public static void gateway(Map<String,Object> call){
        AgentTrace trace=CURRENT.get();
        if(trace!=null && trace.gatewayCalls.size()<32){trace.gatewayCalls.add(Collections.unmodifiableMap(new LinkedHashMap<>(call)));event("gateway",call);}
    }
    public static boolean streaming() { return CURRENT.get()!=null && CURRENT.get().streaming; }
    public static void event(String type,Object data) {
        AgentTrace trace=CURRENT.get(); if(trace!=null && trace.sink!=null) trace.sink.accept(type,data);
    }
    public static void modelStarted() { AgentTrace trace=CURRENT.get(); if(trace!=null) trace.modelCalls++; event("phase",Map.of("label","正在生成回答")); }
    public static void tools(List<String> names) { AgentTrace trace=CURRENT.get(); if(trace!=null) trace.tools.addAll(names); event("tools",names); }
    public static void usage(long input,long output) {
        if(input<0 || output<0) return;
        AgentTrace trace=CURRENT.get(); if(trace!=null) { trace.inputTokens+=input; trace.outputTokens+=output; trace.reportedCalls++; }
    }
    public Map<String,Object> snapshot() {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("modelCalls",modelCalls); result.put("tools",List.copyOf(tools));
        result.put("usageComplete",modelCalls>0 && reportedCalls==modelCalls);
        result.put("reportedCalls",reportedCalls); result.put("inputTokens",reportedCalls==0?null:inputTokens);
        result.put("outputTokens",reportedCalls==0?null:outputTokens);
        result.put("traceId",traceId);result.put("steps",steps.stream().map(Map::copyOf).toList());result.put("gatewayCalls",List.copyOf(gatewayCalls));return result;
    }
    public static final class Step implements AutoCloseable {
        private final AgentTrace owner;
        private final Map<String,Object> value=new LinkedHashMap<>();
        private final long started=System.nanoTime();
        private boolean finished;
        private Step(AgentTrace owner,String kind,String label){
            this.owner=owner;value.put("id",UUID.randomUUID().toString());value.put("kind",kind);value.put("label",label);
            value.put("offsetMs",owner==null?0:(started-owner.started)/1_000_000);value.put("status","RUNNING");value.put("durationMs",0);
            if(owner!=null && owner.steps.size()<128){owner.steps.add(value);emit();}
        }
        public void success(String summary){finish("SUCCESS",summary);}
        public void rejected(String summary){finish("REJECTED",summary);}
        private void finish(String status,String summary){
            if(finished)return;finished=true;value.put("status",status);value.put("durationMs",Math.max(0,(System.nanoTime()-started)/1_000_000));value.put("summary",summary);emit();
        }
        private void emit(){if(owner!=null && owner.sink!=null)owner.sink.accept("step",Map.copyOf(value));}
        @Override public void close(){if(!finished)finish("FAILED","步骤未完成，后续处理已停止");}
    }
    @Override public void close() {
        if(previous==null) CURRENT.remove();
        else {previous.modelCalls+=modelCalls;previous.reportedCalls+=reportedCalls;previous.inputTokens+=inputTokens;previous.outputTokens+=outputTokens;previous.tools.addAll(tools);previous.steps.addAll(steps);previous.gatewayCalls.addAll(gatewayCalls);CURRENT.set(previous);}
    }
}
