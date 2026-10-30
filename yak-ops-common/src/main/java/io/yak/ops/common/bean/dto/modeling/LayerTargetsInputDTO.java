package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Replacement binding of per-environment physical targets for one layer.
 *
 * <p>The submitted list replaces the stored binding wholesale, and at most one entry per
 * environment is accepted. An absent environment simply unbinds it, which publishing reports the
 * next time someone tries to publish into it.
 *
 * @param targets one entry per environment, at most one per environment
 */
public record LayerTargetsInputDTO(
        @Valid @Size(max = 2, message = "每个分层最多绑定开发与生产两个目标") List<Target> targets) {

    /**
     * One environment binding.
     *
     * @param environment DEVELOP or PROD
     * @param dataSourceId datasource used to reach the target
     * @param database default target database resolved for publications
     * @param schema default target schema; required where the engine has an independent schema
     */
    public record Target(
            @NotBlank(message = "请选择发布环境") @Size(max = 16, message = "发布环境标识过长")
            String environment,

            @NotBlank(message = "请选择数据源") @Size(max = 64, message = "数据源标识过长")
            String dataSourceId,

            @NotBlank(message = "请填写目标数据库") @Size(max = 128, message = "目标数据库标识过长")
            String database,

            @Size(max = 128, message = "目标 Schema 标识过长") String schema) {}
}
