package io.yak.ops.business.resource.resolver;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.spi.resource.ResolvedResource;
import io.yak.ops.spi.resource.ResourceDownloadProvider;
import io.yak.ops.spi.resource.ResourceDownloadResult;
import io.yak.ops.spi.resource.ResourceResolver;
import io.yak.ops.spi.resource.TempDirectoryUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

/**
 * Platform implementation of {@link ResourceResolver}.
 * Downloads resource files to local temp directories and validates checksums.
 */
@Component
@ConditionalOnResourceEnabled
public class DefaultResourceResolver implements ResourceResolver {

  private final ResourceDownloadProvider downloadProvider;

  public DefaultResourceResolver(ResourceDownloadProvider downloadProvider) {
    this.downloadProvider = downloadProvider;
  }

  @Override
  public ResolvedResource resolve(long resourceId) {
    ResourceDownloadResult result = downloadProvider.download(resourceId);
    return doResolve(resourceId, result);
  }

  @Override
  public ResolvedResource resolve(long resourceId, int version) {
    ResourceDownloadResult result = downloadProvider.download(resourceId, version);
    return doResolve(resourceId, result);
  }

  private ResolvedResource doResolve(long resourceId, ResourceDownloadResult result) {
    Path tempDir = null;
    try {
      tempDir = Files.createTempDirectory("yak-task-");
      Path localFile = tempDir.resolve(result.fileName());
      String actualChecksum;
      try (InputStream in = result.inputStream()) {
        actualChecksum = copyWithChecksum(in, localFile);
      }
      // Validate checksum integrity
      if (result.checksum() != null && !result.checksum().isBlank()
          && !result.checksum().equalsIgnoreCase(actualChecksum)) {
        TempDirectoryUtils.deleteRecursively(tempDir);
        throw new IllegalStateException(
            "Resource #" + resourceId + " checksum mismatch: expected "
                + result.checksum() + ", actual " + actualChecksum);
      }
      return new ResolvedResource(
          localFile,
          result.fileName(),
          result.suffix(),
          result.fileSize(),
          actualChecksum
      );
    } catch (Exception e) {
      if (tempDir != null) {
        try {
          TempDirectoryUtils.deleteRecursively(tempDir);
        } catch (IOException cleanupError) {
          e.addSuppressed(cleanupError);
        }
      }
      throw new IllegalStateException(
          "Failed to resolve resource #" + resourceId + " to local file", e);
    }
  }

  /** Copy stream to file while computing SHA-256 checksum, avoiding a second read pass. */
  private static String copyWithChecksum(InputStream in, Path target) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream din = new DigestInputStream(in, digest);
           var out = Files.newOutputStream(target)) {
        din.transferTo(out);
      }
      StringBuilder hex = new StringBuilder(64);
      for (byte b : digest.digest()) {
        hex.append(String.format("%02x", b & 0xff));
      }
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
