package io.yak.ops.business.quality.service;

import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityQuery;
import io.yak.ops.business.quality.repository.QualityRepository;
import io.yak.ops.business.quality.service.support.QualityViewMapper;
import io.yak.ops.common.bean.dto.quality.QualityTemplateDTO;
import io.yak.ops.common.bean.vo.quality.QualityTemplateVO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ConditionalOnQualityEnabled
@Service
public class QualityTemplateService {

  private final QualityRepository repository;

  public QualityTemplateService(QualityRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityTemplateVO.ListView list(QualityTemplateDTO.Query request) {
    QualityQuery.Template query = request == null
        ? new QualityQuery.Template(null, null, null)
        : new QualityQuery.Template(request.keyword(), request.dimension(), request.scope());
    var all = repository.listTemplates(new QualityQuery.Template(null, null, null));
    Map<String, Long> dimensions = new LinkedHashMap<>();
    all.forEach(template -> dimensions.merge(template.dimension(), 1L, Long::sum));
    return new QualityTemplateVO.ListView(
        repository.listTemplates(query).stream().map(QualityViewMapper::template).toList(),
        new QualityTemplateVO.Summary(all.size(), dimensions));
  }

  @Transactional(readOnly = true, transactionManager = "yakBusinessTransactionManager")
  public QualityTemplateVO.Template get(long id) {
    return repository.findTemplate(id)
        .map(QualityViewMapper::template)
        .orElseThrow(() -> new IllegalArgumentException("规则模板不存在：" + id));
  }
}
