package io.yak.ops.business.resource.resolver;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceDownload;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.service.ResourceService;
import io.yak.ops.business.resource.service.impl.ResourceServiceSupport;
import io.yak.ops.spi.resource.ResourceDownloadProvider;
import io.yak.ops.spi.resource.ResourceDownloadResult;
import org.springframework.stereotype.Component;

/** Platform implementation of {@link ResourceDownloadProvider}. */
@Component
@ConditionalOnResourceEnabled
class DefaultResourceDownloadProvider implements ResourceDownloadProvider {

  private final ResourceService resourceService;
  private final ResourceServiceSupport support;

  DefaultResourceDownloadProvider(ResourceService resourceService, ResourceServiceSupport support) {
    this.resourceService = resourceService;
    this.support = support;
  }

  @Override
  public ResourceDownloadResult download(long resourceId) {
    ResourceNode node = support.requireFile(resourceId);
    ResourceDownload download = resourceService.download(resourceId);
    return toResult(node, download);
  }

  @Override
  public ResourceDownloadResult download(long resourceId, int version) {
    // Stage 1 strategy: download the current latest version
    // and include version metadata for validation by the caller
    ResourceNode node = support.requireFile(resourceId);
    // Validate that the current version matches the requested version.
    // If the resource has no version metadata, we cannot guarantee consistency.
    if (node.getVersion() == null) {
      throw new IllegalStateException(
          "Resource #" + resourceId + " has no version metadata; "
              + "cannot verify requested version " + version);
    }
    if (node.getVersion() != version) {
      throw new IllegalStateException(
          "Resource #" + resourceId + " version mismatch: requested version "
              + version + ", current version " + node.getVersion()
              + ". The resource has been updated since the task was published.");
    }
    ResourceDownload download = resourceService.download(resourceId);
    return toResult(node, download);
  }

  private ResourceDownloadResult toResult(ResourceNode node, ResourceDownload download) {
    return new ResourceDownloadResult(
        download.fileName(),
        node.getSuffix() != null ? node.getSuffix() : "",
        download.fileSize(),
        node.getChecksum() != null ? node.getChecksum() : "",
        node.getVersion() != null ? node.getVersion() : 0,
        download.inputStream());
  }
}
