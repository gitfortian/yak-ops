package io.yak.ops.business.resource.service.impl;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.config.ResourceProperties;
import io.yak.ops.business.resource.domain.ResourceContent;
import io.yak.ops.business.resource.domain.ResourceDownload;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.repository.ResourceRepository;
import io.yak.ops.business.resource.storage.StorageOperatorRegistry;
import io.yak.ops.business.resource.util.ResourcePathUtils;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import io.yak.ops.spi.resource.ResourceFileSyncAction;
import io.yak.ops.spi.storage.StorageOperator;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 资源文件上传、下载、替换和在线编辑操作；只处理 Domain 与存储 SPI。 */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
class ResourceFileOperations {

  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  private static final int MAX_CONTENT_LINES = 2000;

  private final ResourceRepository repository;
  private final StorageOperatorRegistry storageRegistry;
  private final ResourceServiceSupport support;
  private final ResourceProperties properties;

  ResourceNode upload(Long parentId, String requestedName, String description, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "上传文件不能为空");
    }
    ensureFileSize(file.getSize());
    ResourceServiceSupport.ParentContext parent = support.parent(parentId);
    String sourceName = StringUtils.hasText(requestedName) ? requestedName : file.getOriginalFilename();
    String name = ResourcePathUtils.normalizeName(sourceName);
    support.ensureNameAvailable(parent.id, name, null);
    String fullPath = ResourcePathUtils.childPath(parent.fullPath, name);
    String storagePath = ResourcePathUtils.storagePath(fullPath);
    String contentType = contentType(file.getContentType());
    String checksum = checksum(file);
    StorageOperator operator = storageRegistry.require(parent.storageType);

    try (InputStream inputStream = file.getInputStream()) {
      support.storageRun(
          () -> operator.upload(storagePath, inputStream, file.getSize(), contentType, false));
    } catch (IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED, "读取上传文件失败", exception);
    }

    try {
      ResourceNode resource =
          support.newResource(
              parent.id,
              name,
              fullPath,
              ResourceNodeType.FILE,
              parent.storageType,
              storagePath,
              contentType,
              ResourcePathUtils.suffix(name),
              file.getSize(),
              checksum,
              description);
      support.insert(resource);
      support.dispatch(resource, ResourceFileSyncAction.CREATED, null);
      return resource;
    } catch (RuntimeException exception) {
      support.cleanupCreatedObject(operator, storagePath, false);
      throw exception;
    }
  }

  ResourceNode createContent(
      Long parentId,
      String requestedName,
      String description,
      String requestedContentType,
      String text) {
    if (text == null) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "在线创建内容不能为空");
    }
    byte[] content = text.getBytes(StandardCharsets.UTF_8);
    ensureFileSize(content.length);
    ResourceServiceSupport.ParentContext parent = support.parent(parentId);
    String name = ResourcePathUtils.normalizeName(requestedName);
    ensureEditableName(name);
    support.ensureNameAvailable(parent.id, name, null);
    String fullPath = ResourcePathUtils.childPath(parent.fullPath, name);
    String storagePath = ResourcePathUtils.storagePath(fullPath);
    String contentType = contentType(requestedContentType);
    StorageOperator operator = storageRegistry.require(parent.storageType);

    support.storageRun(
        () ->
            operator.upload(
                storagePath,
                new ByteArrayInputStream(content),
                content.length,
                contentType,
                false));
    try {
      ResourceNode resource =
          support.newResource(
              parent.id,
              name,
              fullPath,
              ResourceNodeType.FILE,
              parent.storageType,
              storagePath,
              contentType,
              ResourcePathUtils.suffix(name),
              (long) content.length,
              checksum(content),
              description);
      support.insert(resource);
      support.dispatch(resource, ResourceFileSyncAction.CREATED, null);
      return resource;
    } catch (RuntimeException exception) {
      support.cleanupCreatedObject(operator, storagePath, false);
      throw exception;
    }
  }

  ResourceNode replaceFile(Long id, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResourceException(ResourceErrorCode.INVALID_NAME, "更新文件不能为空");
    }
    ensureFileSize(file.getSize());
    ResourceNode resource = support.requireFile(id);
    String contentType = contentType(file.getContentType());
    StorageOperator operator = storageRegistry.require(resource.getStorageType());
    try (InputStream inputStream = file.getInputStream()) {
      support.storageRun(
          () ->
              operator.upload(
                  resource.getStoragePath(), inputStream, file.getSize(), contentType, true));
    } catch (IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED, "读取更新文件失败", exception);
    }
    resource.setContentType(contentType);
    resource.setFileSize(file.getSize());
    resource.setChecksum(checksum(file));
    resource.setVersion(support.nextVersion(resource));
    resource.setUpdateTime(LocalDateTime.now());
    if (!repository.update(resource)) {
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
    support.dispatch(resource, ResourceFileSyncAction.UPDATED, resource.getFullPath());
    return resource;
  }

  ResourceContent updateContent(Long id, String text) {
    if (text == null) {
      throw new ResourceException(ResourceErrorCode.CONTENT_NOT_EDITABLE, "文件内容不能为空");
    }
    ResourceNode resource = support.requireFile(id);
    ensureEditable(resource);
    byte[] content = text.getBytes(StandardCharsets.UTF_8);
    ensureFileSize(content.length);
    if (content.length > properties.getEditableMaxBytes()) {
      throw new ResourceException(
          ResourceErrorCode.CONTENT_NOT_EDITABLE,
          "在线编辑内容不能超过 " + properties.getEditableMaxBytes() + " 字节");
    }
    StorageOperator operator = storageRegistry.require(resource.getStorageType());
    support.storageRun(
        () ->
            operator.upload(
                resource.getStoragePath(),
                new ByteArrayInputStream(content),
                content.length,
                contentType(resource.getContentType()),
                true));
    resource.setFileSize((long) content.length);
    resource.setChecksum(checksum(content));
    resource.setVersion(support.nextVersion(resource));
    resource.setUpdateTime(LocalDateTime.now());
    if (!repository.update(resource)) {
      throw new ResourceException(ResourceErrorCode.UPDATE_FAILED);
    }
    support.dispatch(resource, ResourceFileSyncAction.UPDATED, resource.getFullPath());
    return content(resource, text, 0, lineCount(text), false);
  }

  ResourceContent getContent(Long id, int skipLineNum, int limit) {
    ResourceNode resource = support.requireFile(id);
    ensureEditable(resource);
    if (resource.getFileSize() != null
        && resource.getFileSize() > properties.getEditableMaxBytes()) {
      throw new ResourceException(
          ResourceErrorCode.CONTENT_NOT_EDITABLE, "文件超过在线查看大小限制");
    }
    int normalizedSkip = Math.max(0, skipLineNum);
    int normalizedLimit = Math.max(1, Math.min(limit, MAX_CONTENT_LINES));
    StorageOperator operator = storageRegistry.require(resource.getStorageType());
    try (InputStream inputStream =
            support.storageGet(() -> operator.download(resource.getStoragePath()));
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      for (int index = 0; index < normalizedSkip; index++) {
        if (reader.readLine() == null) {
          return content(resource, "", normalizedSkip, 0, false);
        }
      }
      List<String> lines = new ArrayList<>();
      String line;
      while (lines.size() <= normalizedLimit && (line = reader.readLine()) != null) {
        lines.add(line);
      }
      boolean hasMore = lines.size() > normalizedLimit;
      if (hasMore) {
        lines.remove(lines.size() - 1);
      }
      return content(
          resource,
          String.join("\n", lines),
          normalizedSkip,
          lines.size(),
          hasMore);
    } catch (IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED, "读取资源内容失败", exception);
    }
  }

  ResourceDownload download(Long id) {
    ResourceNode resource = support.requireFile(id);
    StorageOperator operator = storageRegistry.require(resource.getStorageType());
    InputStream inputStream = support.storageGet(() -> operator.download(resource.getStoragePath()));
    return new ResourceDownload(
        resource.getName(),
        contentType(resource.getContentType()),
        resource.getFileSize() == null ? 0L : resource.getFileSize(),
        inputStream);
  }

  private void ensureFileSize(long size) {
    if (size < 0L || size > properties.getMaxFileSize()) {
      throw new ResourceException(
          ResourceErrorCode.FILE_TOO_LARGE,
          "最大允许 " + properties.getMaxFileSize() + " 字节");
    }
  }

  private void ensureEditable(ResourceNode resource) {
    ensureEditableName(resource.getName());
  }

  private void ensureEditableName(String name) {
    String suffix = ResourcePathUtils.suffix(name);
    if (!StringUtils.hasText(suffix)
        || properties.getEditableSuffixes() == null
        || !properties.getEditableSuffixes().contains(suffix.toLowerCase(Locale.ROOT))) {
      throw new ResourceException(ResourceErrorCode.CONTENT_NOT_EDITABLE, name);
    }
  }

  private String checksum(MultipartFile file) {
    try (InputStream inputStream = file.getInputStream()) {
      return checksum(inputStream);
    } catch (IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED, "计算文件校验值失败", exception);
    }
  }

  private String checksum(byte[] content) {
    return checksum(new ByteArrayInputStream(content));
  }

  private String checksum(InputStream inputStream) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
        byte[] buffer = new byte[8192];
        while (digestInputStream.read(buffer) >= 0) {
          // 读取完整输入流以更新摘要。
        }
      }
      StringBuilder value = new StringBuilder(64);
      for (byte item : digest.digest()) {
        value.append(String.format("%02x", item & 0xff));
      }
      return value.toString();
    } catch (NoSuchAlgorithmException | IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED, "计算文件校验值失败", exception);
    }
  }

  private ResourceContent content(
      ResourceNode resource,
      String text,
      int skipLineNum,
      int lineCount,
      boolean hasMore) {
    return new ResourceContent(
        resource.getId(),
        resource.getFullPath(),
        text,
        skipLineNum,
        lineCount,
        hasMore);
  }

  private String contentType(String value) {
    return StringUtils.hasText(value) ? value.trim() : DEFAULT_CONTENT_TYPE;
  }

  private int lineCount(String content) {
    if (content == null || content.isEmpty()) {
      return 0;
    }
    return (int) content.lines().count();
  }
}
