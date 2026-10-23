package io.yak.ops.boot.controller;

import io.yak.ops.common.bean.dto.task.SchemaPreviewDTO;
import io.yak.ops.common.constant.CommonConstants;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.result.Result;
import io.yak.ops.plugin.task.api.TaskValidationException;
import io.yak.ops.plugin.task.datasync.DataSyncTaskPlugin;
import io.yak.ops.plugin.task.datasync.plan.SchemaPreview;
import io.yak.ops.plugin.task.datasync.validation.JobConfigValidator;
import io.yak.ops.plugin.task.datasync.validation.SchemaValidationException;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authorized metadata-only DDL preview; execution belongs to an admitted Task Instance. */
@RestController
@RequestMapping(CommonConstants.API_PREFIX + "/data-sync/schema")
public class DataSyncSchemaController {
    @Resource
    private DataSyncTaskPlugin plugin;

    /**
     * Previews target structures without acquiring claims, executing DDL or starting jobs.
     *
     * @param request explicit product configuration
     * @return read-only, ordered target DDL previews
     */
    @PostMapping("/preview")
    public Result<List<SchemaPreview>> preview(@Valid @RequestBody SchemaPreviewDTO request) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        try {
            return Result.success(plugin.preview(request.getParameters(), workspaceId));
        } catch (SchemaValidationException failure) {
            throw new TaskValidationException(
                    "SCHEMA_PREVIEW", JobConfigValidator.safeSchemaMessage(failure.failure()), failure);
        } catch (RuntimeException failure) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
    }
}
