package io.yak.ops.common.bean.dto.modeling.metric;

import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable attributes of one atomic metric. */
@Getter
@Setter
public class MetricAtomicInputDTO {
    /** Business process of the same Workspace that produces the measure. */
    @NotBlank(message = "必填参数不能为空")
    @Size(max = 64, message = "参数长度超过限制")
    private String processId;

    /** Workspace-unique code inside atomic metrics. */
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 64, message = "文本长度超过限制")
    private String enCode;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String cnName;

    /** Logical type the measure returns. */
    @NotNull(message = "必填参数不能为空")
    private DataType dataType;

    /** Aggregate that is part of the statistical caliber. */
    @NotNull(message = "必填参数不能为空")
    private AggregateType aggregateType;

    /** Optional measurement unit from the data-standards list. */
    @Size(max = 64, message = "度量单位标识过长")
    private String measureUnitId;

    /** Accountable owner; defaults to the caller when omitted. */
    @Size(max = 64, message = "负责人标识过长")
    private String ownerId;

    @Size(max = 500, message = "文本长度超过限制")
    private String description;
}
