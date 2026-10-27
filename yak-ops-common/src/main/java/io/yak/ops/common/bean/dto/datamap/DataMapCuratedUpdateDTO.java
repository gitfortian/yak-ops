package io.yak.ops.common.bean.dto.datamap;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Replacement of the user-owned fields of one asset. */
@Getter
@Setter
public class DataMapCuratedUpdateDTO {

    /** Business description; null or blank clears it. */
    @Size(max = 2000)
    private String description;

    /** Accountable Workspace member; null clears the owner. */
    @Size(max = 64)
    private String ownerId;

    /** Data domain of 数仓规划; null clears the classification. */
    @Size(max = 64)
    private String domainId;
}
