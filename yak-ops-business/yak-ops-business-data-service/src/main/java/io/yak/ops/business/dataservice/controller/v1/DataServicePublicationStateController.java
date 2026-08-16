package io.yak.ops.business.dataservice.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.ops.business.dataservice.service.DataServicePublicationService;
import io.yak.ops.business.dataservice.service.DataServicePublicationService.PublicationState;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only publication-state endpoint used by authoring surfaces to show Runtime drift. */
@Tag(name = "数据服务发布状态")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping("/api/v1/data-service/publication")
public class DataServicePublicationStateController {

  private final DataServicePublicationService publicationService;

  @Operation(summary = "查询已发布来源与 Runtime 的同步状态")
  @GetMapping("/state")
  public Result<PublicationState> state(
      @RequestParam("sourceType") String sourceType,
      @RequestParam("sourceRef") String sourceRef) {
    return Result.success(publicationService.state(sourceType, sourceRef));
  }
}
