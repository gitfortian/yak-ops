package io.yak.ops.business.quality.template;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Template;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityTemplateRepository;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Read-side access to built-in quality rule templates. */
@Component
@ConditionalOnQualityEnabled
public class QualityTemplateReader {
  private final QualityTemplateRepository repository;

  public QualityTemplateReader(QualityTemplateRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public TemplateList list(QualityQuery.Template query) {
    List<Template> all = repository.listTemplates(new QualityQuery.Template(null, null, null));
    Map<String, Long> dimensions = new LinkedHashMap<>();
    all.forEach(template -> dimensions.merge(template.dimension(), 1L, Long::sum));
    List<Template> records = repository.listTemplates(
        query == null ? new QualityQuery.Template(null, null, null) : query);
    return new TemplateList(records, new Summary(all.size(), dimensions));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public Template require(long id) {
    return repository.findTemplate(id)
        .orElseThrow(() -> new IllegalArgumentException("规则模板不存在：" + id));
  }

  public record TemplateList(List<Template> records, Summary summary) {
    public TemplateList {
      records = records == null ? List.of() : List.copyOf(records);
    }
  }

  public record Summary(int total, Map<String, Long> dimensions) {
    public Summary {
      dimensions = dimensions == null
          ? Map.of()
          : Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
    }
  }
}
