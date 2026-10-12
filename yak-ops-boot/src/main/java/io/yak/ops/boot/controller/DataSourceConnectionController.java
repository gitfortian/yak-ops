package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.dto.datasource.DataSourceBatchIdsDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceConnectTestDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceBatchConnectTestResultVO;
import io.yak.ops.common.bean.vo.datasource.DataSourceConnectionPropertyKeysVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes datasource connection properties and connectivity checks. */
@Tag(name = "数据源管理接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-source")
public class DataSourceConnectionController {

    @Resource
    private DataSourceService dataSourceService;

    @Operation(summary = "查询数据源高级连接参数候选项")
    @GetMapping("/connection-property-keys")
    public Result<DataSourceConnectionPropertyKeysVO> connectionPropertyKeys(@RequestParam("dbType") String dbType) {
        return Result.success(dataSourceService.queryConnectionPropertyKeys(dbType));
    }

    @Operation(summary = "测试已保存数据源连接")
    @RequestMapping(
            value = "/{id}/connect-test",
            method = {RequestMethod.GET, RequestMethod.POST})
    public Result<Boolean> testConnection(@PathVariable("id") String id) {
        return Result.success(dataSourceService.testConnection(id));
    }

    @Operation(summary = "批量测试已保存数据源连接")
    @PostMapping("/batch-connect-test")
    public Result<List<DataSourceBatchConnectTestResultVO>> batchTestConnection(
            @Valid @RequestBody DataSourceBatchIdsDTO dto) {
        return Result.success(dataSourceService.batchTestConnections(dto));
    }

    @Operation(summary = "使用连接参数测试数据源连接")
    @PostMapping("/connect-test-with-param")
    public Result<Boolean> testConnection(@Valid @RequestBody DataSourceConnectTestDTO dto) {
        return Result.success(dataSourceService.testConnection(dto));
    }
}
