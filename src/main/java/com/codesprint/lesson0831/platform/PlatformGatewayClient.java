package com.codesprint.lesson0831.platform;

import com.fasterxml.jackson.databind.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Component
@Profile("platform")
public class PlatformGatewayClient {
    private final URI base; private final String key; private final ObjectMapper json;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public PlatformGatewayClient(Environment env,ObjectMapper json) {
        base=URI.create(env.getRequiredProperty("platform.gateway-url")); key=env.getRequiredProperty("platform.gateway-key");this.json=json;
        if(!Set.of("http","https").contains(base.getScheme()) || base.getHost()==null || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null)
            throw new IllegalArgumentException("Gateway 地址无效");
    }
    public JsonNode request(String path,Object body) {
        try {
            var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(body==null?5:60))
                .header("X-API-Key",key).header("X-Idempotency-Key",UUID.randomUUID().toString());
            request.header("X-Trace-Id",com.codesprint.lesson0831.observability.AgentTrace.traceId());
            if(body!=null)request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=http.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200 || response.statusCode()>=300 || response.body().length()>4_000_000)
                throw new PlatformException(503,"GATEWAY_UNAVAILABLE","模型网关暂不可用，请检查配置与运行状态");
            return json.readTree(response.body());
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new PlatformException(503,"INTERRUPTED","调用已停止");}
        catch(java.io.IOException e){throw new PlatformException(503,"GATEWAY_UNAVAILABLE","无法连接模型网关");}
    }
    public List<List<Double>> embed(List<String> input) {
        JsonNode response=request("/api/gateway/platform/embeddings",Map.of("input",input));
        JsonNode data=response.path("data");
        if(!data.isArray() || data.size()!=input.size())throw new PlatformException(503,"EMBEDDING_INVALID","向量响应不完整");
        List<List<Double>> result=new ArrayList<>(Collections.nCopies(input.size(),null));
        for(JsonNode item:data) {
            if(!item.path("index").isIntegralNumber() || !item.path("index").canConvertToInt())throw new PlatformException(503,"EMBEDDING_INVALID","向量序号无效");
            int index=item.path("index").asInt(-1);JsonNode values=item.path("embedding");
            if(index<0 || index>=input.size() || result.get(index)!=null || !values.isArray() || values.size()<1 || values.size()>4096)
                throw new PlatformException(503,"EMBEDDING_INVALID","向量响应无效");
            List<Double> vector=new ArrayList<>();
            for(JsonNode value:values){if(!value.isNumber() || !Double.isFinite(value.doubleValue()))throw new PlatformException(503,"EMBEDDING_INVALID","向量数据无效");vector.add(value.doubleValue());}
            if(vector.stream().allMatch(v -> v==0))throw new PlatformException(503,"EMBEDDING_INVALID","向量为空");
            result.set(index,List.copyOf(vector));
        }
        int dimension=result.get(0).size();
        if(result.stream().anyMatch(v -> v.size()!=dimension))throw new PlatformException(503,"EMBEDDING_INVALID","向量维数不一致");
        return List.copyOf(result);
    }
}
