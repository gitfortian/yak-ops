package io.yak.ops.common.bean.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Metadata and optional executable parameters for one Task Definition update. */
@Data
public class UpdateDefinitionDTO {
    /** Updated display name of the definition. */
    @NotBlank
    @Size(max = 128)
    private String name;

    /** Optional plugin-specific JSON; omitting it retains the current parameters. */
    private String parameters;

    /** JSON remains the default; HOCON is interpreted by the selected Task Plugin. */
    @Pattern(regexp = "(?i)JSON|HOCON", message = "Unsupported parameters format")
    private String parametersFormat;

    /** Optional human-readable description. */
    @Size(max = 500)
    private String remark;
}
