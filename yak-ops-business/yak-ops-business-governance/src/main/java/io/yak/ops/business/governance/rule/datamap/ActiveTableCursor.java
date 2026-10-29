package io.yak.ops.business.governance.rule.datamap;

import io.yak.ops.business.datamap.governance.GovernanceTableSnapshot;
import io.yak.ops.business.governance.rule.GovernanceRuleContext;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keyset iteration over the ACTIVE catalog.
 *
 * <p>A rule must enumerate every eligible target before it may resolve anything, so the loop only
 * stops when a page comes back short.
 */
final class ActiveTableCursor {

    static final int PAGE_SIZE = 200;

    private ActiveTableCursor() {}

    static void forEach(GovernanceRuleContext context, Consumer<GovernanceTableSnapshot> action) {
        String afterId = "";
        while (true) {
            List<GovernanceTableSnapshot> page =
                    context.dataMap().activeTables(context.workspaceId(), afterId, PAGE_SIZE);
            page.forEach(action);
            if (page.size() < PAGE_SIZE) {
                return;
            }
            afterId = page.get(page.size() - 1).id();
        }
    }
}
