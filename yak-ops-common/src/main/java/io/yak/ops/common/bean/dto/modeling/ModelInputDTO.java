package io.yak.ops.common.bean.dto.modeling;

import io.yak.ops.common.enums.modeling.ModelType;
import io.yak.ops.common.enums.modeling.StoragePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Editable DWD/DIM model fields. The table name is submitted as a suffix: the layer prefix is
 * derived from the selected layer so a renamed layer cannot leave stale table names behind.
 */
@Getter
@Setter
public class ModelInputDTO {
    @NotNull(message = "请选择模型类型")
    private ModelType type;

    @NotBlank(message = "请选择数仓分层")
    @Size(max = 64, message = "数仓分层标识过长")
    private String layerId;

    @NotBlank(message = "请选择业务分类")
    @Size(max = 64, message = "业务分类标识过长")
    private String categoryId;

    @Size(max = 64, message = "业务过程标识过长")
    private String processId;

    @Size(max = 64, message = "数据域标识过长")
    private String domainId;

    @Size(max = 64, message = "数据集市标识过长")
    private String datamartId;

    @Size(max = 64, message = "概念维度标识过长")
    private String dimensionId;

    @NotBlank(message = "请输入表名后缀")
    @Pattern(regexp = "^[a-z][a-z0-9_]{0,40}$", message = "表名后缀须以小写字母开头，仅含小写字母、数字或下划线，最多41位")
    private String nameSuffix;

    @NotBlank(message = "请输入表中文名")
    @Size(max = 128, message = "表中文名不能超过128个字符")
    private String cnName;

    @NotNull(message = "请选择存储策略")
    private StoragePolicy storagePolicy;

    @NotNull(message = "请输入生命周期")
    @Min(value = 1, message = "生命周期不能小于1天")
    @Max(value = 36000, message = "生命周期不能超过36000天")
    private Integer lifecycleDays;

    @Size(max = 64, message = "负责人标识过长")
    private String ownerId;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    /**
     * Engine-specific physical table properties as a JSON object (Doris bucket count, replica
     * count, data model). Validated structurally here and against the target dialect at
     * publication time.
     */
    @Size(max = 2000, message = "物理属性过长")
    private String physicalProperties;

    @Valid
    private List<ModelFieldInputDTO> fields;
}
