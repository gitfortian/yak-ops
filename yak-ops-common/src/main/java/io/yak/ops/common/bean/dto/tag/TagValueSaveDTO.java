package io.yak.ops.common.bean.dto.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Creation and rename request of a tag value. */
@Getter
@Setter
public class TagValueSaveDTO {

    /** Term name, unique inside the key. */
    @NotBlank
    @Size(max = 128)
    private String name;
}
