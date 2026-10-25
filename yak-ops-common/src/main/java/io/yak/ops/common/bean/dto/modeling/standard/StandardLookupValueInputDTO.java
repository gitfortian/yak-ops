package io.yak.ops.common.bean.dto.modeling.standard;

import lombok.Getter;
import lombok.Setter;

/** One submitted value of a lookup table; the whole list is replaced on every save. */
@Getter
@Setter
public class StandardLookupValueInputDTO {
    private String valueCode;

    private String cnName;

    private String enName;

    private String meaning;
}
