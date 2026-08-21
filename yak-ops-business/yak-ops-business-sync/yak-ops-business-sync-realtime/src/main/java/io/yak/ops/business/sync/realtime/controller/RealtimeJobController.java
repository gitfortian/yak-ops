package io.yak.ops.business.sync.realtime.controller;
import com.fasterxml.jackson.databind.JsonNode;import io.yak.framework.common.Result;import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;import io.yak.ops.business.sync.realtime.engine.RealtimeEngineGateway;import io.yak.ops.business.sync.realtime.service.RealtimeJobService;import jakarta.validation.Valid;import java.util.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/realtime-sync")
public class RealtimeJobController {
 private final RealtimeJobService service;public RealtimeJobController(RealtimeJobService s){service=s;}
 public record SaveRequest(Long id,String name,String description,@Valid CdcPipelineSpec spec){}
 @PostMapping("/draft") public Result<Long> draft(@Valid @RequestBody SaveRequest r){return Result.success(service.save(r.id(),r.name(),r.description(),r.spec()));}
 @PutMapping("/{id}") public Result<Long> save(@PathVariable long id,@Valid @RequestBody SaveRequest r){return Result.success(service.save(id,r.name(),r.description(),r.spec()));}
 @GetMapping("/{id}") public Result<Map<String,Object>> detail(@PathVariable long id){return Result.success(service.get(id));}
 @GetMapping public Result<List<Map<String,Object>>> page(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.success(service.page(page,size));}
 @PostMapping("/{id}/publish") public Result<Boolean> publish(@PathVariable long id){service.publish(id);return Result.success(true);}
 @PostMapping("/{id}/validate") public Result<RealtimeEngineGateway.GatewayResult> validate(@PathVariable long id){return Result.success(service.validate(id));}
 @PostMapping("/{id}/start") public Result<Map<String,Object>> start(@PathVariable long id,@RequestHeader(value="Idempotency-Key",required=false)String key){return Result.success(service.start(id,key));}
 @PostMapping("/{id}/stop") public Result<Boolean> stop(@PathVariable long id){service.stop(id);return Result.success(true);}
 @PostMapping("/{id}/restart") public Result<Map<String,Object>> restart(@PathVariable long id){return Result.success(service.restart(id));}
 @DeleteMapping("/{id}") public Result<Boolean> delete(@PathVariable long id){service.delete(id);return Result.success(true);}
 @GetMapping("/{id}/events") public Result<List<Map<String,Object>>> events(@PathVariable long id){return Result.success(service.events(id));}
 @GetMapping("/runtime/capabilities") public Result<JsonNode> capabilities(){return Result.success(service.capabilities());}
 @GetMapping("/runtime/logs") public Result<JsonNode> logs(@RequestParam(defaultValue="200")int tail){return Result.success(service.logs(tail));}
}
