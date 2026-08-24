package io.yak.ops.business.development.task;

import io.yak.ops.spi.task.model.TaskDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Calculates the stable digest used to identify an immutable normalized TaskDefinition snapshot. */
@Component
public class TaskDefinitionDigestCalculator {

  public String calculate(TaskDefinition definition) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      updateDigest(digest, definition.taskType());
      updateDigest(digest, Integer.toString(definition.schemaVersion()));
      updateDigest(digest, definition.content());
      updateDigest(digest, definition.configJson());
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private void updateDigest(MessageDigest digest, String value) {
    if (value != null) {
      digest.update(value.getBytes(StandardCharsets.UTF_8));
    }
    digest.update((byte) 0);
  }
}
