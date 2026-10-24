import type { UserPreferenceRecord } from "@/service/preference";
import { GLOBAL_PRODUCT_MENU } from "./navigation";

export const PRODUCT_MENU_SCENE = "PRODUCT_MENU" as const;

export function upsertFavoritePreference(
  preferences: UserPreferenceRecord[],
  next: UserPreferenceRecord,
) {
  if (!preferences.some(item => item.itemKey === next.itemKey)) return [...preferences, next];
  return preferences.map(item => item.itemKey === next.itemKey ? next : item);
}

export function updateFavoritePreference(preferences: UserPreferenceRecord[], productId: string) {
  const previous = preferences.find(item => item.itemKey === productId);
  const favorite = !previous?.favorite;
  const sortOrder = favorite
    ? Math.max(0, ...preferences.filter(item => item.favorite).map(item => item.sortOrder)) + 1
    : 0;
  const next: UserPreferenceRecord = {
    scene: PRODUCT_MENU_SCENE,
    itemKey: productId,
    favorite,
    sortOrder,
    useCount: previous?.useCount ?? 0,
    lastUsedTime: previous?.lastUsedTime,
  };
  return { favorite, preferences: upsertFavoritePreference(preferences, next) };
}

export function favoriteProducts(preferences: UserPreferenceRecord[]) {
  return preferences
    .filter(item => item.favorite)
    .sort((a, b) => a.sortOrder - b.sortOrder)
    .flatMap(item => {
      const product = GLOBAL_PRODUCT_MENU.find(p => p.id === item.itemKey);
      return product ? [product] : [];
    });
}
