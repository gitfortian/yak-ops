export type LoginFocusState = "idle" | "userName" | "userPassword";
export type LoginResultState = "idle" | "submitting" | "failure" | "success";
export type LoginSceneState =
  | "idle"
  | "inputFocus"
  | "passwordVisible"
  | "submitting"
  | "failure"
  | "success";

export const LOGIN_FAILURE_MOTION_MS = 700;
export const LOGIN_SUCCESS_MOTION_MS = 960;

// Resolve once so CSS expressions and the animation loop have the same priority.
// Visibility belongs to the field value's display mode, not just input focus.
export function resolveLoginSceneState(
  focusState: LoginFocusState,
  resultState: LoginResultState,
  passwordVisible: boolean,
): LoginSceneState {
  if (resultState !== "idle") return resultState;
  if (passwordVisible) return "passwordVisible";
  // Field identity stays in the form; both fields share one visual state.
  return focusState === "idle" ? "idle" : "inputFocus";
}
