package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Publication request resolved through the environment the user picked.
 *
 * <p>The datasource, database and schema are never submitted by the client: they come from the
 * target the model's layer binds for that environment, so an inferred address is always one the
 * layer owner pre-approved. {@code acknowledgeDataLoss} is required by the service when the
 * computed plan drops columns; sending it elsewhere changes nothing.
 *
 * @param environment DEVELOP or PROD
 * @param acknowledgeDataLoss explicit acceptance of data loss for DROP COLUMN plans
 */
public record PublicationTargetDTO(
        @NotBlank(message = "请选择发布环境") @Size(max = 16, message = "发布环境标识过长")
        String environment,

        Boolean acknowledgeDataLoss) {}
