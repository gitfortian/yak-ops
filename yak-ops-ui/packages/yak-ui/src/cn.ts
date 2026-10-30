import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Joins conditional class names and resolves conflicting Tailwind utilities so
 * the last declaration wins.
 *
 * A plain `join(" ")` leaves the winner to stylesheet order: a caller passing
 * `w-36` to a component that already ships `w-full` would silently keep the
 * component's width. Tailwind only guarantees source order within a utility
 * family, so callers must be able to override — hence the merge step.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
