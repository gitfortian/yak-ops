package io.yak.ops.common.bean.dto.modeling.metric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable attributes of one composite metric. */
@Getter
@Setter
public class MetricCompositeInputDTO {
    /** Workspace-unique code inside composite metrics. */
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 64, message = "文本长度超过限制")
    private String enCode;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String cnName;

    /**
     * Arithmetic expression over derived-metric codes of the same Workspace; design-time data that
     * is parsed and reference-resolved but never evaluated.
     */
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 2048, message = "文本长度超过限制")
    private String expression;

    /** Accountable owner; defaults to the caller when omitted. */
    @Size(max = 64, message = "负责人标识过长")
    private String ownerId;

    @Size(max = 500, message = "文本长度超过限制")
    private String description;
}
