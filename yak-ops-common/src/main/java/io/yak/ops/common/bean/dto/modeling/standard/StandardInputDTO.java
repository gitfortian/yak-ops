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
}
