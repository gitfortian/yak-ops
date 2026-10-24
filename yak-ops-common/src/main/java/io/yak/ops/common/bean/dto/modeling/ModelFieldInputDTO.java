package io.yak.ops.common.bean.dto.modeling;

import io.yak.ops.common.enums.modeling.AggregateType;
import io.yak.ops.common.enums.modeling.standard.DataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * One submitted field of a model.
 *
 * <p>{@code enCode}, {@code dataType} and {@code lengthPrecision} are overwritten from the bound
 * standard when {@code standardId} is set, so they are not required on the request.
 */
@Getter
@Setter
public class ModelFieldInputDTO {
    @Size(max = 64, message = "字段英文编码过长")
    private String enCode;

    @NotBlank(message = "请输入字段中文名")
    @Size(max = 128, message = "字段中文名不能超过128个字符")
    private String cnName;

    private DataType dataType;

    @Size(max = 32, message = "长度或精度不能超过32个字符")
    private String lengthPrecision;

    private Boolean isPrimaryKey;

    private Boolean isNotNull;

    private Boolean isPartition;

    /** Measure function; accepted for DWS and ADS only and validated against the resolved type. */
    private AggregateType aggregateType;

    @Size(max = 64, message = "数据标准标识过长")
    private String standardId;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;
}
