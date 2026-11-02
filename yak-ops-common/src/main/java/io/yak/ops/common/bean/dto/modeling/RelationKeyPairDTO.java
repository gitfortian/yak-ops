package io.yak.ops.common.bean.dto.modeling;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** One ordered join key pair; both codes are field en_code values of their own model. */
@Getter
@Setter
public class RelationKeyPairDTO {
    @NotBlank(message = "请提供事实表侧字段")
    @Size(max = 64, message = "字段编码过长")
    private String fromFieldCode;

    @NotBlank(message = "请提供维表侧字段")
    @Size(max = 64, message = "字段编码过长")
    private String toFieldCode;
}
