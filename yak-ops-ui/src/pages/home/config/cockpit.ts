const LIFECYCLE_ROUTES: Record<string, string> = {
  'data-source': '/data-source',
  integration: '/sync/batch-link-up',
  development: '/data-development',
  workflow: '/workflow/definitions',
  quality: '/data-quality/overview',
  asset: '/data-analysis/data-catalog',
  service: '/data-service/overview',
  consumption: '/dashboard',
};

const ATTENTION_ROUTES: Record<string, string> = {
  'data-source-connection': '/data-source',
  'offline-failures': '/sync/batch-link-up',
  'workflow-failures': '/workflow/instances',
  'quality-execution-failures': '/data-quality/execution',
  'quality-issues': '/data-quality/overview',
};

export function getHomeLifecycleRoute(key: string): string {
  return LIFECYCLE_ROUTES[key] || '/home';
}

export function getHomeAttentionRoute(key: string): string {
  return ATTENTION_ROUTES[key] || '/home';
}
