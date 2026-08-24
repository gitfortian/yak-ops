package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.observability.DataServiceOverviewReader;
import io.yak.ops.business.dataservice.observability.DataServiceOverviewReader.Overview;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "数据服务")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service/overview")
public class DataServiceOverviewController {
  private final DataServiceOverviewReader reader;
  @Operation(summary = "查询数据服务运行概览")
  @GetMapping
  public Result<Overview> overview(@RequestParam(value = "range", defaultValue = "24h") String range) {
    return Result.success(reader.overview(range));
  }
}
