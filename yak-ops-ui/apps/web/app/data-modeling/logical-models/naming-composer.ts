/**
 * Pure helpers behind the layer naming-rule composer in the model editor. The backend
 * (`NamingRuleMatcher`) accepts a suffix of `词根_过程编码` plus an optional `_限定词`, at most
 * 41 characters overall; the qualifier tail itself may start with a digit (`trade_order_1d`),
 * so these helpers mirror the backend grammar instead of a stricter reading of it.
 */

/**
 * The qualifier tail: lowercase letters, digits and underscores. No leading-letter rule of its
 * own — the letter head is already guaranteed by the root word that starts the suffix.
 */
export function sanitizeQualifier(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9_]/g, "");
}

/** `root_process` plus an optional `_qualifier`; empty until both root and process are set. */
export function composeSuffix(rootCode: string, processCode: string, qualifier: string): string {
  const head = rootCode && processCode ? `${rootCode}_${processCode}` : "";
  return head ? (qualifier ? `${head}_${qualifier}` : head) : "";
}

/**
 * Splits a saved suffix back into its qualifier tail given the root and process it was built
 * from; "" when the head does not match, including an unknown root or an unresolved process.
 */
export function decomposeSuffix(
  savedSuffix: string,
  rootCode: string | null,
  processCode: string,
): string {
  const head = composeSuffix(rootCode ?? "", processCode, "");
  return head && savedSuffix.startsWith(head)
    ? savedSuffix.slice(head.length).replace(/^_/, "")
    : "";
}

/** The saved suffix's leading segment, used as the keyword to find a root beyond the first page. */
export function rootSearchSegment(savedSuffix: string): string {
  return savedSuffix.split("_")[0] ?? "";
}

/**
 * Room left for the qualifier once the root and process segments are in place; 0 when full.
 *
 * <p>The 41-character budget applies to the suffix alone, and the layer prefix is stored beside it
 * rather than inside it, so the prefix must not be subtracted here: doing so would reject a name the
 * server accepts.
 */
export function qualifierMaxLength(prefix: string, rootCode: string, processCode: string): number {
  const head = composeSuffix(rootCode, processCode, "");
  if (!head) return 41;
  return Math.max(0, 41 - head.length - 1);
}
