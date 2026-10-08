import HttpUtils from "@/service/http/HttpUtils";

import type {
  UserPreferenceFavoritePayload,
  UserPreferenceRecord,
  UserPreferenceScene,
} from "./types";

export type * from "./types";

const USER_PREFERENCE_API_PREFIX = "/api/v1/user-preferences";

const WITHOUT_WORKSPACE_HEADER = {
  workspaceHeader: "omit" as const,
};

const BACKGROUND_PREFERENCE_OPTIONS = {
  ...WITHOUT_WORKSPACE_HEADER,
  skipErrorHandler: true,
};

export const listUserPreferences = (scene: UserPreferenceScene): Promise<UserPreferenceRecord[]> =>
  HttpUtils.getData<UserPreferenceRecord[]>(
    `${USER_PREFERENCE_API_PREFIX}?scene=${encodeURIComponent(scene)}`,
    BACKGROUND_PREFERENCE_OPTIONS,
  );

export const updateUserPreferenceFavorite = (
  scene: UserPreferenceScene,
  itemKey: string,
  payload: UserPreferenceFavoritePayload,
): Promise<UserPreferenceRecord> =>
  HttpUtils.putData<UserPreferenceRecord>(
    `${USER_PREFERENCE_API_PREFIX}/${encodeURIComponent(scene)}/${encodeURIComponent(itemKey)}/favorite`,
    payload,
    WITHOUT_WORKSPACE_HEADER,
  );

export const recordUserPreferenceUsage = (
  scene: UserPreferenceScene,
  itemKey: string,
): Promise<UserPreferenceRecord> =>
  HttpUtils.postData<UserPreferenceRecord>(
    `${USER_PREFERENCE_API_PREFIX}/${encodeURIComponent(scene)}/${encodeURIComponent(itemKey)}/use`,
    undefined,
    BACKGROUND_PREFERENCE_OPTIONS,
  );
