package io.yak.ops.business.dataset.service.support;

import io.yak.ops.business.dataset.DatasetField;
import io.yak.ops.business.dataset.DatasetFieldDefinition;
import io.yak.ops.business.dataset.DatasetFieldDataType;
import io.yak.ops.business.dataset.DatasetFieldRole;
import io.yak.ops.business.dataset.DatasetService;
import io.yak.ops.business.dataset.repository.DatasetRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Normalizes reusable Dataset field contracts without mixing the rules into persistence code. */
@Component
@RequiredArgsConstructor
public class DatasetFieldNormalizer {

  private final DatasetRepository repository;

  public List<DatasetService.FieldSpec> normalize(
      long datasetId,
      List<DatasetService.FieldSpec> values) {
    if (values == null || values.isEmpty()) return List.of();

    Map<String, String> existingFieldIds = existingFieldIds(datasetId);
    List<DatasetService.FieldSpec> normalized = new ArrayList<>(values.size());
    Set<String> physicalNames = new HashSet<>();
    Set<String> fieldIds = new HashSet<>();
    for (DatasetService.FieldSpec value : values) {
      if (value == null) throw new IllegalArgumentException("Dataset 字段不能为空");
      String physicalName = required(value.physicalName(), "physicalName", 128);
      String physicalKey = physicalName.toLowerCase(Locale.ROOT);
      if (!physicalNames.add(physicalKey)) {
        throw new IllegalArgumentException("Dataset 字段重复：" + physicalName);
      }

      String fieldId = value.fieldId();
      if (fieldId == null || fieldId.isBlank()) {
        fieldId = existingFieldIds.getOrDefault(physicalKey, stableFieldId(datasetId, physicalKey));
      }
      fieldId = required(fieldId, "fieldId", 64);
      if (!fieldIds.add(fieldId)) throw new IllegalArgumentException("fieldId 重复：" + fieldId);

      String displayName = value.displayName();
      if (displayName == null || displayName.isBlank()) displayName = physicalName;
      displayName = required(displayName, "displayName", 200);

      String description = normalizeDescription(value.description());
      normalized.add(new DatasetService.FieldSpec(
          fieldId,
          physicalName,
          displayName,
          value.dataType() == null ? DatasetFieldDataType.UNKNOWN : value.dataType(),
          value.nullable(),
          description,
          value.defaultRole() == null ? DatasetFieldRole.DIMENSION : value.defaultRole()));
    }
    return List.copyOf(normalized);
  }

  public boolean sameFields(
      List<DatasetField> current,
      List<DatasetService.FieldSpec> requested) {
    if (current.size() != requested.size()) return false;
    for (int index = 0; index < current.size(); index++) {
      DatasetField left = current.get(index);
      DatasetService.FieldSpec right = requested.get(index);
      if (!Objects.equals(left.fieldId(), right.fieldId())
          || !Objects.equals(left.physicalName(), right.physicalName())
          || !Objects.equals(left.displayName(), right.displayName())
          || left.dataType() != right.dataType()
          || left.nullable() != right.nullable()
          || !Objects.equals(left.description(), right.description())
          || left.defaultRole() != right.defaultRole()) {
        return false;
      }
    }
    return true;
  }

  public List<DatasetFieldDefinition> definitions(List<DatasetService.FieldSpec> fields) {
    if (fields == null || fields.isEmpty()) return List.of();
    return fields.stream()
        .map(field -> new DatasetFieldDefinition(
            field.fieldId(),
            field.physicalName(),
            field.displayName(),
            field.dataType(),
            field.nullable(),
            field.description(),
            field.defaultRole()))
        .toList();
  }

  private Map<String, String> existingFieldIds(long datasetId) {
    Map<String, String> result = new HashMap<>();
    repository.findDataset(datasetId).ifPresent(dataset -> {
      if (dataset.currentVersionId() == null) return;
      repository.listFields(dataset.currentVersionId()).forEach(field ->
          result.put(field.physicalName().toLowerCase(Locale.ROOT), field.fieldId()));
    });
    return result;
  }

  private String stableFieldId(long datasetId, String physicalKey) {
    return UUID.nameUUIDFromBytes(
        ("dataset:" + datasetId + ":" + physicalKey).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private String normalizeDescription(String value) {
    if (value == null || value.isBlank()) return null;
    String normalized = value.trim();
    if (normalized.length() > 1000) throw new IllegalArgumentException("字段描述不能超过 1000 个字符");
    return normalized;
  }

  private String required(String value, String fieldName, int maxLength) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " 不能为空");
    String normalized = value.trim();
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException(fieldName + " 不能超过 " + maxLength + " 个字符");
    }
    return normalized;
  }
}
