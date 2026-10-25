package io.yak.ops.common.bean.dto.modeling.standard;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.common.enums.modeling.standard.DataType;
import io.yak.ops.common.enums.modeling.standard.SecurityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Editable attributes; category and audit identity are supplied by their owning boundaries. */
@Getter
@Setter
public class StandardInputDTO {
    @NotBlank(message = "必填文本不能为空")
    @Size(max = 128, message = "文本长度超过限制")
    private String cnName;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 64, message = "文本长度超过限制")
    private String enCode;

    @NotNull(message = "必填参数不能为空")
    private DataType dataType;

    /** Required for DECIMAL, where it must carry both a length and a precision, such as 18,2. */
    @Size(max = 32, message = "文本长度超过限制")
    private String lengthPrecision;

    @NotNull(message = "必填参数不能为空")
    @JsonProperty("isNotNull")
    private Boolean isNotNull;

    @NotNull(message = "必填参数不能为空")
    private SecurityLevel securityLevel;

    @NotBlank(message = "必填文本不能为空")
    @Size(max = 64, message = "文本长度超过限制")
    private String ownerId;

    @Size(max = 2000, message = "文本长度超过限制")
    private String businessCaliber;

    /** Descriptive business preset; never becomes a physical DEFAULT clause. */
    @Size(max = 2048, message = "业务预设值不能超过2048个字符")
    private String defaultValue;

    @Size(max = 64, message = "父标准标识长度超过限制")
    private String parentStandardId;

    @Size(max = 64, message = "标准代码标识长度超过限制")
    private String lookupId;

    @Size(max = 64, message = "度量单位标识长度超过限制")
    private String measureUnitId;
}
