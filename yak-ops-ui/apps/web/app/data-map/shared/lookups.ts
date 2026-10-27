import { useQuery } from "@tanstack/react-query";
import { getOwners, getPlanningTree } from "@/service/data-modeling/warehouse-planning";
import { listTags } from "@/service/data-map/tags";
import {
  listDataSourceDatabases,
  listDataSourceSchemas,
  listDataSources,
} from "@/service/datasource";

import { dataMapKeys } from "./keys";
import { useDataMapScope } from "./scope";

/** Taxonomy is bounded by the planning taxonomy, so it is read once per Workspace and kept warm. */
const TAXONOMY_STALE_TIME = 5 * 60 * 1000;
const OPTION_STALE_TIME = 5 * 60 * 1000;
const DATASOURCE_OPTION_LIMIT = 200;

export interface DomainOption {
  id: string;
  name: string;
}

export interface DomainCategory {
  id: string;
  name: string;
  domains: DomainOption[];
}

/** Data domains of 数仓规划, the only taxonomy level this catalog classifies assets with. */
export function useDomainTaxonomy() {
  const { scope, ready } = useDataMapScope();
  const query = useQuery({
    queryKey: dataMapKeys.taxonomy(scope),
    queryFn: ({ signal }) => getPlanningTree(signal),
    enabled: ready,
    staleTime: TAXONOMY_STALE_TIME,
  });

  const categories: DomainCategory[] = (query.data?.categories ?? []).map((category) => ({
    id: category.id,
    name: category.name,
    domains: category.children.map((domain) => ({ id: domain.id, name: domain.name })),
  }));
  const domainNames = new Map<string, string>();
  categories.forEach((category) =>
    category.domains.forEach((domain) => domainNames.set(domain.id, domain.name)),
  );
  const domains = [...domainNames].map(([id, name]) => ({ id, name }));

  return { categories, domains, domainNames, ...query };
}

export interface DatasourceOption {
  id: string;
  name: string;
  environment: string;
}

export function useDatasourceOptions() {
  const { scope, ready } = useDataMapScope();
  const query = useQuery({
    queryKey: dataMapKeys.datasources(scope),
    queryFn: ({ signal }) =>
      listDataSources({ pageNo: 1, pageSize: DATASOURCE_OPTION_LIMIT }, signal),
    enabled: ready,
    staleTime: OPTION_STALE_TIME,
  });

  const options: DatasourceOption[] = (query.data?.bizData ?? [])
    .filter((record): record is typeof record & { id: string } => Boolean(record.id))
    .map((record) => ({
      id: record.id,
      name: record.name ?? record.id,
      environment: record.environment ?? "",
    }));

  return { options, ...query };
}

export function useTagOptions() {
  const { scope, ready } = useDataMapScope();
  return useQuery({
    queryKey: dataMapKeys.tags(scope),
    queryFn: ({ signal }) => listTags(signal),
    enabled: ready,
    staleTime: OPTION_STALE_TIME,
  });
}

/** Active Workspace members eligible as an asset owner; the same picker planning uses. */
export function useOwnerOptions() {
  const { scope, ready } = useDataMapScope();
  return useQuery({
    queryKey: dataMapKeys.owners(scope),
    queryFn: ({ signal }) => getOwners(signal),
    enabled: ready,
    staleTime: OPTION_STALE_TIME,
  });
}

const catalogKeys = {
  databases: (scope: readonly string[], datasourceId: string) =>
    ["data-source-catalog", "databases", ...scope, datasourceId] as const,
  schemas: (scope: readonly string[], datasourceId: string, database: string) =>
    ["data-source-catalog", "schemas", ...scope, datasourceId, database] as const,
};

export function useDatabases(datasourceId: string) {
  const { scope, ready } = useDataMapScope();
  return useQuery({
    queryKey: catalogKeys.databases(scope, datasourceId),
    queryFn: () => listDataSourceDatabases(datasourceId),
    enabled: ready && Boolean(datasourceId),
    staleTime: OPTION_STALE_TIME,
  });
}

export function useSchemas(datasourceId: string, database: string) {
  const { scope, ready } = useDataMapScope();
  return useQuery({
    queryKey: catalogKeys.schemas(scope, datasourceId, database),
    queryFn: () => listDataSourceSchemas(datasourceId, database || undefined),
    enabled: ready && Boolean(datasourceId),
    staleTime: OPTION_STALE_TIME,
  });
}
