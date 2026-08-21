package io.yak.ops.business.sync.realtime.engine;
import com.fasterxml.jackson.databind.JsonNode;
public interface RealtimeEngineGateway {
 JsonNode health(); JsonNode capabilities(); GatewayResult validate(String yaml); GatewayResult deploy(String yaml,String idempotencyKey); JsonNode status(); GatewayResult stop(String jobId); JsonNode logs(int tail);
 record GatewayResult(int status,JsonNode body,boolean uncertain){}
}
