package io.yak.ops.business.sync.offline.domain.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Immutable data range owned by a BatchExecution. */
public sealed interface BatchScope
    permits BatchScope.FullSelection,
        BatchScope.DataWindow,
        BatchScope.PartitionScope,
        BatchScope.CursorRange {

  String canonicalValue();

  default String fingerprint() {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonicalValue().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException("生成 BatchScope fingerprint 失败", exception);
    }
  }

  static FullSelection fullSelection() {
    return new FullSelection();
  }

  static DataWindow dataWindow(LocalDateTime startInclusive, LocalDateTime endExclusive) {
    return new DataWindow(startInclusive, endExclusive);
  }

  static PartitionScope partitions(List<String> partitions) {
    return new PartitionScope(partitions);
  }

  static CursorRange cursorRange(
      String cursorId, String afterExclusive, String throughInclusive) {
    return new CursorRange(cursorId, afterExclusive, throughInclusive);
  }

  record FullSelection() implements BatchScope {
    @Override
    public String canonicalValue() {
      return "FULL_SELECTION";
    }
  }

  record DataWindow(LocalDateTime startInclusive, LocalDateTime endExclusive)
      implements BatchScope {

    public DataWindow {
      startInclusive = Objects.requireNonNull(startInclusive, "startInclusive 不能为空");
      endExclusive = Objects.requireNonNull(endExclusive, "endExclusive 不能为空");
      if (!startInclusive.isBefore(endExclusive)) {
        throw new IllegalArgumentException("DataWindow 必须满足 start < end");
      }
    }

    @Override
    public String canonicalValue() {
      return "DATA_WINDOW|" + startInclusive + "|" + endExclusive;
    }
  }

  record PartitionScope(List<String> partitions) implements BatchScope {

    public PartitionScope {
      Objects.requireNonNull(partitions, "partitions 不能为空");
      List<String> normalized =
          partitions.stream()
              .map(PartitionScope::requirePartition)
              .distinct()
              .sorted(Comparator.naturalOrder())
              .toList();
      if (normalized.isEmpty()) throw new IllegalArgumentException("PartitionScope 不能为空");
      partitions = List.copyOf(normalized);
    }

    @Override
    public String canonicalValue() {
      return "PARTITIONS|"
          + partitions.stream()
              .map(BatchScope::encode)
              .reduce((left, right) -> left + "," + right)
              .orElse("");
    }

    private static String requirePartition(String value) {
      if (value == null || value.trim().isEmpty()) {
        throw new IllegalArgumentException("partition 不能为空");
      }
      return value.trim();
    }
  }

  record CursorRange(String cursorId, String afterExclusive, String throughInclusive)
      implements BatchScope {

    public CursorRange {
      cursorId = requireText(cursorId, "cursorId 不能为空");
      afterExclusive = requireText(afterExclusive, "afterExclusive 不能为空");
      throughInclusive = requireText(throughInclusive, "throughInclusive 不能为空");
      if (afterExclusive.equals(throughInclusive)) {
        throw new IllegalArgumentException("CursorRange 起止位置不能相同");
      }
    }

    @Override
    public String canonicalValue() {
      return "CURSOR_RANGE|"
          + encode(cursorId)
          + "|"
          + encode(afterExclusive)
          + "|"
          + encode(throughInclusive);
    }
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String requireText(String value, String message) {
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
    return value.trim();
  }
}
