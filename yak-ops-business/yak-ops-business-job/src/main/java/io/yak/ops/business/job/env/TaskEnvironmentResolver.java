package io.yak.ops.business.job.env;

import java.util.Map;

/** Resolves the global environment visible to task runtime contexts. */
public interface TaskEnvironmentResolver {

  Map<String, String> resolveMergedEnv();
}
