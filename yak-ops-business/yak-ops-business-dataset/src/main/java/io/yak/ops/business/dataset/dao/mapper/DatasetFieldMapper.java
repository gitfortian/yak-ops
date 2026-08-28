package io.yak.ops.business.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataset.dao.model.DatasetFieldPO;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DatasetFieldMapper extends BaseMapper<DatasetFieldPO> {

  @Insert({
      "<script>",
      "INSERT INTO yak_dataset_field ",
      "(field_id, version_id, physical_name, display_name, data_type, `nullable`, description, default_role, sort_order) VALUES ",
      "<foreach collection='fields' item='field' separator=','>",
      "(#{field.fieldId}, #{field.versionId}, #{field.physicalName}, #{field.displayName}, ",
      "#{field.dataType}, #{field.nullable}, #{field.description}, #{field.defaultRole}, #{field.sortOrder})",
      "</foreach>",
      "</script>"
  })
  int insertBatch(@Param("fields") List<DatasetFieldPO> fields);
}
