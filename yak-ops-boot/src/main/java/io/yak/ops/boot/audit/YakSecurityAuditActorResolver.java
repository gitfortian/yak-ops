package io.yak.ops.boot.audit;

import io.yak.framework.security.authentication.AuthenticationManager;
import io.yak.ops.business.audit.AuditActor;
import io.yak.ops.business.audit.AuditActorResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Adapts Yak Security's stable login identity into the shared Audit actor contract. */
@Component
@ConditionalOnBean(AuthenticationManager.class)
@RequiredArgsConstructor
public class YakSecurityAuditActorResolver implements AuditActorResolver {

  private static final Logger log = LoggerFactory.getLogger(YakSecurityAuditActorResolver.class);

  private final AuthenticationManager authenticationManager;

  @Override
  public AuditActor currentActor() {
    try {
      if (!authenticationManager.isLogin()) {
        return AuditActor.system();
      }
      Long userId = authenticationManager.getLoginUserId();
      String userName = authenticationManager.getLoginUsername();
      return new AuditActor(userId == null ? null : String.valueOf(userId), userName, "USER");
    } catch (RuntimeException exception) {
      log.warn("Unable to resolve Yak Security audit actor; using system actor", exception);
      return AuditActor.system();
    }
  }
}
