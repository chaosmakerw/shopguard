package com.codesprint.lesson0831.platform;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlatformGatewayClientTest {
    private final ObjectMapper json=new ObjectMapper();
    private PlatformGatewayClient client(String response)throws Exception{
        JsonNode fixture=json.readTree(response);
        return new PlatformGatewayClient(new MockEnvironment().withProperty("platform.gateway-url","http://127.0.0.1:1").withProperty("platform.gateway-key","test-key"),json){
            @Override public JsonNode request(String path,Object body){return fixture;}
        };
    }
    @Test void fractionalTextAndOutOfRangeIndicesNeverTruncate()throws Exception{
        for(String index:List.of("0.5","\"0\"","2147483648","-1")){
            var client=client("{\"data\":[{\"index\":"+index+",\"embedding\":[0.2,0.4]}]}");
            assertThrows(PlatformException.class,()->client.embed(List.of("rule")));
        }
    }
    @Test void vectorOrderIsRestoredAndInvalidVectorsAreRejected()throws Exception{
        var valid=client("{\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}");
        assertEquals(List.of(List.of(1.0,0.0),List.of(0.0,1.0)),valid.embed(List.of("first","second")));
        for(String vector:List.of("[0,0]","[\"0.2\",0.4]","[]")){
            var invalid=client("{\"data\":[{\"index\":0,\"embedding\":"+vector+"}]}");
            assertThrows(PlatformException.class,()->invalid.embed(List.of("rule")));
        }
    }
}
