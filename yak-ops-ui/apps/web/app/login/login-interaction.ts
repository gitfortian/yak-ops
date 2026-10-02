export type LoginFocusState = "idle" | "userName" | "userPassword";
export type LoginResultState = "idle" | "submitting" | "failure" | "success";
export type LoginSceneState =
  | "idle"
  | "userName"
  | "passwordHidden"
  | "passwordVisible"
  | "submitting"
  | "failure"
  | "success";

export const LOGIN_FAILURE_MOTION_MS = 700;
export const LOGIN_SUCCESS_MOTION_MS = 960;
const PURPLE_PASSWORD_ENTER_MS = 720;

// Resolve once so CSS expressions and the animation loop have the same priority.
// Visibility belongs to the field value's display mode, not just input focus.
export function resolveLoginSceneState(
  focusState: LoginFocusState,
  resultState: LoginResultState,
  passwordVisible: boolean,
): LoginSceneState {
  if (resultState !== "idle") return resultState;
  if (passwordVisible) return "passwordVisible";
  if (focusState === "userPassword") return "passwordHidden";
  return focusState;
}

// Enter, settle, then hold a regular attentive pose; repeated typing does not replay this.
export function getPurplePasswordBow(elapsedMs: number) {
  const progress = Math.max(0, Math.min(1, elapsedMs / PURPLE_PASSWORD_ENTER_MS));
  const phase = progress < 0.32 ? progress / 0.32 : (1 - progress) / 0.68;
  return phase * phase * (3 - 2 * phase);
}
