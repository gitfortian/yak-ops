import type { TagKey } from "@/service/tags";

/** One key with the values the user selected under it; the catalog filter is one clause per group. */
export interface TagFilterGroup {
  keyId: string;
  keyName: string;
  values: { id: string; name: string }[];
}

/**
 * Groups the selected value ids by their key, keeping the dictionary order of `GET /keys`.
 *
 * <p>Values of one key are OR-ed and different keys are AND-ed on the server, so the client keeps
 * the selection as one flat id list and groups it here for display and for the request.
 */
export function tagFilterGroups(
  keys: readonly TagKey[],
  selectedValueIds: readonly string[],
): TagFilterGroup[] {
  const selected = new Set(selectedValueIds);
  const groups: TagFilterGroup[] = [];
  for (const key of keys) {
    const values = key.values
      .filter((value) => selected.has(value.id))
      .map((value) => ({ id: value.id, name: value.name }));
    if (values.length === 0) continue;
    groups.push({ keyId: key.id, keyName: key.name, values });
  }
  return groups;
}

/** Trigger label of the tag filter: one `key: values` clause per group, keys joined by a semicolon. */
export function tagFilterLabel(groups: readonly TagFilterGroup[]): string {
  if (groups.length === 0) return "全部标签";
  return groups
    .map((group) => `${group.keyName}: ${group.values.map((value) => value.name).join("、")}`)
    .join("；");
}
