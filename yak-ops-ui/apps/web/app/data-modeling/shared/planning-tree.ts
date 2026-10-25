import type { PlanningTree, ProcessNode } from "@/service/data-modeling/warehouse-planning/types";

export interface CategoryChoice {
  id: string;
  name: string;
}

export interface DomainChoice {
  id: string;
  name: string;
  categoryId: string;
  categoryName: string;
}

export interface ProcessChoice {
  id: string;
  name: string;
  /** Abbreviation the naming rule composes into the table-name suffix. */
  code: string;
  domainId: string;
}

export interface DatamartChoice {
  id: string;
  name: string;
}

export function categoriesOf(tree: PlanningTree): CategoryChoice[] {
  return tree.categories.map((category) => ({ id: category.id, name: category.name }));
}

export function domainsOf(tree: PlanningTree, categoryId: string): DomainChoice[] {
  const category = tree.categories.find((node) => node.id === categoryId);
  return (category?.children ?? []).map((domain) => ({
    id: domain.id,
    name: domain.name,
    categoryId,
    categoryName: category?.name ?? "",
  }));
}

export function processesOf(tree: PlanningTree, domainId: string): ProcessChoice[] {
  for (const category of tree.categories)
    for (const domain of category.children)
      if (domain.id === domainId)
        return domain.children.map((process: ProcessNode) => ({
          id: process.id,
          name: process.name,
          code: process.code,
          domainId,
        }));
  return [];
}

export function datamartsOf(tree: PlanningTree): DatamartChoice[] {
  return tree.datamarts.map((node) => ({ id: node.id, name: node.name }));
}

/** All data domains with their category, used as a flat selector. */
export function allDomainsOf(tree: PlanningTree): DomainChoice[] {
  return tree.categories.flatMap((category) =>
    category.children.map((domain) => ({
      id: domain.id,
      name: domain.name,
      categoryId: category.id,
      categoryName: category.name,
    })),
  );
}
