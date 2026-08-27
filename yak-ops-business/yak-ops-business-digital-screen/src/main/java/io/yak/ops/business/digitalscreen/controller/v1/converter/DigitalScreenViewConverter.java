package io.yak.ops.business.digitalscreen.controller.v1.converter;

import io.yak.ops.business.digitalscreen.controller.v1.vo.DigitalScreenViews.DigitalScreenVO;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DigitalScreenViewConverter {

  public DigitalScreenVO screen(DigitalScreen source) {
    return new DigitalScreenVO(
        Long.toString(source.id()),
        source.name(),
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.status().name().toLowerCase(Locale.ROOT),
        source.bindings(),
        source.publishedTime(),
        source.createTime(),
        source.updateTime());
  }
}
