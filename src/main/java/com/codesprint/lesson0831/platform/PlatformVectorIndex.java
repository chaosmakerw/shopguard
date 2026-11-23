package com.codesprint.lesson0831.platform;

import com.fasterxml.jackson.databind.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.security.MessageDigest;
import java.util.*;

/** Qdrant contains vectors; document ownership, visibility and active contents remain in SQL. */
@Component
@Profile("platform")
public class PlatformVectorIndex {
    private final URI base;private final String model;private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    public PlatformVectorIndex(Environment env,ObjectMapper json){
        this.base=URI.create(env.getProperty("QDRANT_URL","http://127.0.0.1:6333"));this.model=env.getRequiredProperty("platform.embedding-model");this.json=json;
        if(!Set.of("http","https").contains(base.getScheme()) || base.getHost()==null || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null)throw new IllegalArgumentException("Qdrant 地址无效");
    }
    String collection(int dimensions) {
        try{return "shopguard_"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(model.getBytes(StandardCharsets.UTF_8))).substring(0,12)+"_"+dimensions;}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public void upsert(String document,List<String> ids,List<List<Double>> vectors) {
        String collection=collection(vectors.get(0).size());
        HttpResponse<String> existing=exchange("GET","/collections/"+collection,null);
        if(existing.statusCode()==404) require(exchange("PUT","/collections/"+collection,Map.of("vectors",Map.of("size",vectors.get(0).size(),"distance","Cosine"))));
        else require(existing);
        List<Object> points=new ArrayList<>();
        for(int i=0;i<ids.size();i++)points.add(Map.of("id",ids.get(i),"vector",vectors.get(i),"payload",Map.of("documentId",document)));
        require(exchange("PUT","/collections/"+collection+"/points?wait=true",Map.of("points",points)));
    }
    public record Hit(String id,double score){}
    public List<Hit> search(List<Double> vector,int count,double threshold) {
        var response=exchange("POST","/collections/"+collection(vector.size())+"/points/search",
            Map.of("vector",vector,"limit",count,"with_payload",false,"score_threshold",threshold));
        if(response.statusCode()==404)return List.of();
        JsonNode result=require(response).path("result"); List<Hit> hits=new ArrayList<>();
        if(!result.isArray() || result.size()>count)throw new PlatformException(503,"VECTOR_INVALID","向量检索结果无效");
        for(JsonNode item:result){double score=item.path("score").asDouble(Double.NaN);if(!item.path("score").isNumber() || !Double.isFinite(score))throw new PlatformException(503,"VECTOR_INVALID","检索分数无效");hits.add(new Hit(item.path("id").asText(),score));}
        return hits;
    }
    public void delete(String document,int dimension) {
        var response=exchange("POST","/collections/"+collection(dimension)+"/points/delete?wait=true",
            Map.of("filter",Map.of("must",List.of(Map.of("key","documentId","match",Map.of("value",document))))));
        if(response.statusCode()!=404)require(response);
    }
    public boolean healthy(){try{return exchange("GET","/healthz",null).statusCode()==200;}catch(RuntimeException e){return false;}}
    private HttpResponse<String> exchange(String method,String path,Object body) {
        try {
            var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15));
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if(body!=null)request.header("Content-Type","application/json");
            return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new PlatformException(503,"VECTOR_UNAVAILABLE","向量库连接中断");}
        catch(java.io.IOException e){throw new PlatformException(503,"VECTOR_UNAVAILABLE","无法连接向量库，请启动 Qdrant");}
    }
    private JsonNode require(HttpResponse<String> response) {
        if(response.statusCode()<200 || response.statusCode()>=300 || response.body().length()>2_000_000)throw new PlatformException(503,"VECTOR_UNAVAILABLE","向量库操作失败");
        try{return json.readTree(response.body());}catch(java.io.IOException e){throw new PlatformException(503,"VECTOR_INVALID","向量库响应无效");}
    }
}
