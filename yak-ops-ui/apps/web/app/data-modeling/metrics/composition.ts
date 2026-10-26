/**
 * Pure composition rules of a derived metric, mirroring the server contract: the composed English
 * code joins the period abbreviation, the modifier abbreviations in submitted order and the atomic
 * code with `_`; the Chinese name concatenates the names without separators.
 */
export const MAX_CODE = 64;
export const MAX_NAME = 128;

export interface CompositionPart {
  periodCode: string;
  modifierCodes: string[];
  atomicCode: string;
  periodName: string;
  modifierNames: string[];
  atomicName: string;
}

export interface Composed {
  enCode: string;
  cnName: string;
}

export function composeComposition(part: CompositionPart): Composed {
  const code = [part.periodCode];
  for (const segment of [...part.modifierCodes, part.atomicCode]) {
    if (!segment || code[code.length - 1] === segment) continue;
    code.push(segment);
  }
  let name = part.periodName;
  for (const segment of [...part.modifierNames, part.atomicName]) {
    if (!segment) continue;
    name = appendWithoutOverlap(name, segment);
  }
  return { enCode: code.join("_"), cnName: name };
}

/**
 * Appends one name segment, keeping the longest suffix of the built name that is also a prefix of
 * the segment only once: a "支付" modifier over a "支付金额" atomic part must not read
 * "最近7天支付支付金额".
 */
function appendWithoutOverlap(current: string, segment: string): string {
  const limit = Math.min(current.length, segment.length);
  for (let overlap = limit; overlap > 0; overlap -= 1) {
    if (current.endsWith(segment.slice(0, overlap))) return current + segment.slice(overlap);
  }
  return current + segment;
}

export function compositionFits(composed: Composed): boolean {
  return composed.enCode.length <= MAX_CODE && composed.cnName.length <= MAX_NAME;
}

/** An honest preview: null when the parts are incomplete, or the composed pair with its verdict. */
export function previewComposition(
  part: Partial<CompositionPart>,
): { composed: Composed; fits: boolean } | null {
  const periodCode = part.periodCode?.trim();
  const atomicCode = part.atomicCode?.trim();
  const periodName = part.periodName?.trim();
  const atomicName = part.atomicName?.trim();
  if (!periodCode || !atomicCode || !periodName || !atomicName) return null;
  const composed = composeComposition({
    periodCode,
    atomicCode,
    periodName,
    atomicName,
    modifierCodes: part.modifierCodes ?? [],
    modifierNames: part.modifierNames ?? [],
  });
  return { composed, fits: compositionFits(composed) };
}
