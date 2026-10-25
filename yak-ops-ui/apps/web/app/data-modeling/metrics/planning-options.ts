import type { PlanningTree } from "@/service/data-modeling/warehouse-planning/types";
import { allDomainsOf } from "../shared/planning-tree";

export interface DomainChoice {
  id: string;
  name: string;
  categoryName: string;
}

export interface ProcessChoice {
  id: string;
  name: string;
  domainName: string;
}

export { allDomainsOf as domainsOf };

/** Every business process with its owning domain, used as a flat selector. */
export function processesOf(tree: PlanningTree): ProcessChoice[] {
  return tree.categories.flatMap((category) =>
    category.children.map((domain) => ({
      id: domain.id,
      name: domain.name,
      domainName: domain.name,
    })),
  );
}

/** One "分类 / 域" label for a domain selector option. */
export function domainLabel(choice: DomainChoice): string {
  return choice.categoryName ? choice.categoryName + " / " + choice.name : choice.name;
}
