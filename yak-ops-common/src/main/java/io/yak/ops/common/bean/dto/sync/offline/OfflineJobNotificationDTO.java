package io.yak.ops.common.bean.dto.sync.offline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

/** Offline Sync task-level notification policy input. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class OfflineJobNotificationDTO {

  /** Master switch. Null inside an explicit policy is normalized to true. */
  private Boolean enabled;

  /** Supported trigger in Stage 4.2: FINAL_FAILURE. */
  private List<String> triggers;

  /** PROJECT_OWNER or EXPLICIT_USERS. */
  private String recipientType;

  /** Durable numeric recipients when recipientType is EXPLICIT_USERS. */
  private List<Long> recipientUserIds;

  /** Stage 4.2 supports the in-app Message Center destination only. */
  private Boolean inAppEnabled;
}
