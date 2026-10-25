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
  /** Category and domain the process sits under, for the flat selector's label. */
  path: string;
}

/** Every data domain with its category, used as a flat selector. */
export { allDomainsOf as domainsOf };

/**
 * Every business process with its owning category and domain, used as a flat selector.
 *
 * <p>A process is the third level of the planning tree (category → domain → process). Reading the
 * second level here would hand the caller domain identities, which the service rejects because an
 * atomic metric anchors to a process.
 */
export function processesOf(tree: PlanningTree): ProcessChoice[] {
  return tree.categories.flatMap((category) =>
    category.children.flatMap((domain) =>
      domain.children.map((process) => ({
        id: process.id,
        name: process.name,
        path: category.name + " / " + domain.name,
      })),
    ),
  );
}

/** One "分类 / 域" label for a domain selector option. */
export function domainLabel(choice: DomainChoice): string {
  return choice.categoryName ? choice.categoryName + " / " + choice.name : choice.name;
}
