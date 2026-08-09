package io.yak.ops.business.resource.domain;

import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.common.enums.resource.ResourceStorageType;
import java.time.LocalDateTime;
import lombok.Data;

/** 资源目录或文件的业务领域对象，与 MyBatis PO 和 HTTP VO 解耦。 */
@Data
public class ResourceNode {

  private Long id;
  private Long parentId;
  private String name;
  private String fullPath;
  private ResourceNodeType nodeType;
  private ResourceStorageType storageType;
  private String storagePath;
  private String contentType;
  private String suffix;
  private Long fileSize;
  private String checksum;
  private String description;
  private Integer version;
  private String gitSyncStatus;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
