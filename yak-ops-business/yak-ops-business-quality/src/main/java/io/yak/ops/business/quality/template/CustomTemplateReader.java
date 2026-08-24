package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.CustomTemplate;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.CustomTemplateRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to custom quality templates. */
@Component
@ConditionalOnQualityEnabled
public class CustomTemplateReader {
  private final CustomTemplateRepository repository;

  public CustomTemplateReader(CustomTemplateRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public CustomTemplateList list(QualityQuery.CustomTemplate query) {
    QualityQuery.CustomTemplate normalized = query == null
        ? new QualityQuery.CustomTemplate(null, null, null, false)
        : query;
    List<CustomTemplate> all = repository.listAllCustom();
    List<CustomTemplate> scope = repository.list(new QualityQuery.CustomTemplate(
        null, null, normalized.folderId(), normalized.folderFilter()));
    Map<String, Long> dimensions = new LinkedHashMap<>();
    scope.forEach(template -> dimensions.merge(template.dimension(), 1L, Long::sum));
    return new CustomTemplateList(
        repository.list(normalized),
        new Summary(scope.size(), repository.countSystem(), all.size(), dimensions));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public CustomTemplate require(long id) {
    return repository.find(id)
        .orElseThrow(() -> new IllegalArgumentException("自定义规则模板不存在：" + id));
  }

  public record CustomTemplateList(List<CustomTemplate> records, Summary summary) {
    public CustomTemplateList {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }

  public record Summary(
      int scopeTotal,
      long systemTotal,
      int customTotal,
      Map<String, Long> dimensions) {
    public Summary {
      dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
    }
  }
}
