package io.yak.ops.boot.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasource.DataSourceService;
import io.yak.ops.common.bean.dto.datasource.DataSourceBatchIdsDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceDTO;
import io.yak.ops.common.bean.dto.datasource.DataSourceQueryDTO;
import io.yak.ops.common.bean.vo.datasource.DataSourceVO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes Workspace-scoped datasource configuration and management operations.
 */
@Tag(name = "数据源管理接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-source")
public class DataSourceController {

    @Resource
    private DataSourceService dataSourceService;

    @Operation(summary = "新增数据源")
    @PostMapping
    public Result<Boolean> create(@Valid @RequestBody DataSourceDTO dto) {
        return Result.success(dataSourceService.addDataSource(dto));
    }

    @Operation(summary = "编辑数据源")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable("id") String id, @Valid @RequestBody DataSourceDTO dto) {
        return Result.success(dataSourceService.updateDataSource(id, dto));
    }

    @Operation(summary = "查询数据源详情")
    @GetMapping("/{id}")
    public Result<DataSourceVO> detail(@PathVariable("id") String id) {
        return Result.success(dataSourceService.queryDataSource(id));
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        return Result.success(dataSourceService.deleteDataSource(id));
    }

    @Operation(summary = "批量删除数据源")
    @PostMapping("/batch-delete")
    public Result<Boolean> batchDelete(@Valid @RequestBody DataSourceBatchIdsDTO dto) {
        return Result.success(dataSourceService.batchDeleteDataSources(dto));
    }

    @Operation(summary = "分页查询数据源")
    @PostMapping("/page")
    public Result<PagingData<DataSourceVO>> page(@Valid @RequestBody DataSourceQueryDTO dto) {
        return Result.success(dataSourceService.queryDataSourcePage(dto));
    }
}
