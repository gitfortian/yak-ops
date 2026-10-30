/** Shapes of the tag dictionary HTTP contract. */

/** One term of a key together with the number of assets currently bound to it. */
export interface TagValue {
  id: string;
  name: string;
  usageCount: number;
}

/** One dimension of the dictionary with every term it holds. */
export interface TagKey {
  id: string;
  name: string;
  values: TagValue[];
}

export type TagBindingOperation = "ADD" | "REMOVE";
