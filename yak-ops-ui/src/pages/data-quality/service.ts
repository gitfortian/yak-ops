/**
 * @deprecated New data-quality code imports direct business data from
 * `@/services/data-quality`. Existing editor, detail and report pages keep the
 * response-envelope facade during incremental migration.
 */
export * from '@/services/data-quality/legacy';
