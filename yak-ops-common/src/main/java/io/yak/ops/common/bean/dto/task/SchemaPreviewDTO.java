package io.yak.ops.common.bean.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Non-secret version-three Data Sync parameters for a read-only DDL preview. */
@Data
public class SchemaPreviewDTO {
    /** Product JSON containing datasource identities and explicit table mappings, never executable SQL. */
    @NotBlank(message = "请填写同步配置")
    @Size(max = 262144, message = "同步配置过大")
    private String parameters;
}
