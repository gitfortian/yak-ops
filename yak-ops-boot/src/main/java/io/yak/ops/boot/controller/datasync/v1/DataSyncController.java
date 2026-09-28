package io.yak.ops.boot.controller.datasync.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.ops.business.datasync.DataSyncService;
import io.yak.ops.common.bean.dto.datasync.DataSyncInstanceQueryDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncMappingPreviewDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncMappingPreviewVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskVO;
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
 * 对外提供数据同步任务定义、实例与字段映射预览 HTTP 接口。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Tag(name = "数据同步任务接口")
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync")
public class DataSyncController {

    @Resource
    private DataSyncService dataSyncService;

    @Operation(summary = "创建数据同步任务")
    @PostMapping("/tasks")
    public Result<DataSyncTaskVO> createTask(@Valid @RequestBody DataSyncTaskDTO dto) {
        return Result.success(dataSyncService.createTask(dto));
    }

    @Operation(summary = "编辑数据同步任务")
    @PutMapping("/tasks/{id}")
    public Result<DataSyncTaskVO> updateTask(@PathVariable("id") String id, @Valid @RequestBody DataSyncTaskDTO dto) {
        return Result.success(dataSyncService.updateTask(id, dto));
    }

    @Operation(summary = "查询数据同步任务详情")
    @GetMapping("/tasks/{id}")
    public Result<DataSyncTaskVO> taskDetail(@PathVariable("id") String id) {
        return Result.success(dataSyncService.queryTask(id));
    }

    @Operation(summary = "分页查询数据同步任务")
    @PostMapping("/tasks/page")
    public Result<PagingData<DataSyncTaskVO>> taskPage(@Valid @RequestBody DataSyncTaskQueryDTO dto) {
        return Result.success(dataSyncService.queryTaskPage(dto));
    }

    @Operation(summary = "手动运行离线同步任务")
    @PostMapping("/tasks/{id}/run")
    public Result<DataSyncInstanceVO> runTask(@PathVariable("id") String id) {
        return Result.success(dataSyncService.runTask(id));
    }

    @Operation(summary = "删除离线同步任务")
    @DeleteMapping("/tasks/{id}")
    public Result<Boolean> deleteTask(@PathVariable("id") String id) {
        return Result.success(dataSyncService.deleteTask(id));
    }

    @Operation(summary = "查询同步实例详情")
    @GetMapping("/instances/{id}")
    public Result<DataSyncInstanceVO> instanceDetail(@PathVariable("id") String id) {
        return Result.success(dataSyncService.queryInstance(id));
    }

    @Operation(summary = "分页查询同步实例")
    @PostMapping("/instances/page")
    public Result<PagingData<DataSyncInstanceVO>> instancePage(@Valid @RequestBody DataSyncInstanceQueryDTO dto) {
        return Result.success(dataSyncService.queryInstancePage(dto));
    }

    @Operation(summary = "停止同步实例")
    @PostMapping("/instances/{id}/cancel")
    public Result<DataSyncInstanceVO> cancelInstance(@PathVariable("id") String id) {
        return Result.success(dataSyncService.cancelInstance(id));
    }

    @Operation(summary = "预览来源与目标表字段自动映射")
    @PostMapping("/tasks/mapping-preview")
    public Result<DataSyncMappingPreviewVO> mappingPreview(@Valid @RequestBody DataSyncMappingPreviewDTO dto) {
        return Result.success(dataSyncService.previewMapping(dto));
    }
}
