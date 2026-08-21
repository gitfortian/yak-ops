package io.yak.ops.business.sync.realtime.engine;
import com.fasterxml.jackson.databind.*;
import io.yak.ops.business.sync.realtime.config.RealtimeSyncProperties;
import java.net.URI; import java.net.http.*; import java.time.Duration;
import org.springframework.stereotype.Component;
@Component
public class HttpRealtimeEngineGateway implements RealtimeEngineGateway {
 private final HttpClient client; private final ObjectMapper json; private final RealtimeSyncProperties p;
 public HttpRealtimeEngineGateway(@org.springframework.beans.factory.annotation.Qualifier("realtimeHttpClient") HttpClient client,@org.springframework.beans.factory.annotation.Qualifier("realtimeObjectMapper") ObjectMapper json,RealtimeSyncProperties p){this.client=client;this.json=json;this.p=p;}
 public JsonNode health(){return get("/health");} public JsonNode capabilities(){return get("/capabilities");} public JsonNode status(){return get("/status");} public JsonNode logs(int tail){return get("/logs?tail="+Math.max(1,Math.min(tail,1000)));}
 public GatewayResult validate(String y){return yaml("/validate",y,null);} public GatewayResult deploy(String y,String k){return yaml("/deploy",y,k);}
 public GatewayResult stop(String id){return postJson("/stop","{\"jobId\":\""+id.replace("\"","")+"\"}");}
 private JsonNode get(String path){try{var r=client.send(req(path).GET().build(),HttpResponse.BodyHandlers.ofString()); check(r.statusCode()); return parse(r.body());}catch(Exception e){throw new GatewayException("Runtime unavailable",e,true);}}
 private GatewayResult yaml(String path,String body,String key){try{var b=req(path).header("Content-Type","text/yaml");if(key!=null)b.header("Idempotency-Key",key);var r=client.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());return result(r);}catch(java.net.http.HttpTimeoutException e){throw new GatewayException("Runtime result uncertain after timeout",e,true);}catch(Exception e){throw new GatewayException("Runtime connection failed",e,false);}}
 private GatewayResult postJson(String path,String body){try{return result(client.send(req(path).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()));}catch(Exception e){throw new GatewayException("Runtime stop result uncertain",e,true);}}
 private GatewayResult result(HttpResponse<String> r){if(r.statusCode()!=200&&r.statusCode()!=202&&r.statusCode()!=409&&r.statusCode()!=422)check(r.statusCode());return new GatewayResult(r.statusCode(),parse(r.body()),false);}
 private HttpRequest.Builder req(String path){return HttpRequest.newBuilder(URI.create(p.getBaseUrl()+path)).timeout(p.getRequestTimeout()).header("Accept","application/json");}
 private JsonNode parse(String s){try{return s==null||s.isBlank()?json.createObjectNode():json.readTree(s);}catch(Exception e){throw new GatewayException("Invalid Runtime response",e,false);}}
 private void check(int s){if(s<200||s>=300)throw new GatewayException("Runtime HTTP "+s,null,false);}
 public static class GatewayException extends RuntimeException {public final boolean uncertain; public GatewayException(String m,Throwable c,boolean u){super(m,c);uncertain=u;}}
}
