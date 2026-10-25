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
  const code = [part.periodCode, ...part.modifierCodes, part.atomicCode].join("_");
  const name = [part.periodName, ...part.modifierNames, part.atomicName].join("");
  return { enCode: code, cnName: name };
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
