/** Failure-only scheduling. Both result bridges reuse the same root-SVG snapshot geometry. */
import {
  captureLoginPose,
  createLoginPosePainter,
  mixLoginPoseSnapshot as mixFailureSnapshot,
  type LoginPoseSnapshot as FailureSnapshot,
} from "./login-pose-snapshot";

export {
  normalizeAuthoredLoginPath,
  mixLoginPoseSnapshot as mixFailureSnapshot,
} from "./login-pose-snapshot";
export type { LoginPoseSnapshot as FailureSnapshot } from "./login-pose-snapshot";

export const LOGIN_FAILURE_TRANSITION_END = "yak-login-failure-transition-end";
const ENTER_MS = 180;
const RECOVER_MS = 280;
type Phase = "none" | "enter" | "hold" | "recover" | "frozen";

export function createLoginFailureTransition(scene: HTMLElement, id: string) {
  const painter = createLoginPosePainter(scene, id, "failure");
  let phase: Phase = "none";
  let source: FailureSnapshot | null = null;
  let painted: FailureSnapshot | null = null;
  let startedAt = 0;
  const draw = (snapshot: FailureSnapshot) => {
    painted = snapshot;
    painter.draw(snapshot);
  };
  const setPhase = (next: Phase) => {
    phase = next;
    scene.dataset.failurePhase = next;
  };
  const settle = (failure: boolean) => {
    const wasActive = phase !== "none";
    setPhase(failure ? "hold" : "none");
    source = null;
    painted = null;
    if (wasActive && !failure) scene.dispatchEvent(new Event(LOGIN_FAILURE_TRANSITION_END));
  };
  return {
    captureVisible: () =>
      painted ?? captureLoginPose(scene, phase === "hold" ? "failure" : "normal"),
    active: () => phase !== "none" && phase !== "hold",
    settle,
    begin(next: "enter" | "recover" | "frozen", snapshot: FailureSnapshot | null, now: number) {
      if (!snapshot) {
        settle(next === "enter");
        return;
      }
      source = snapshot;
      startedAt = now;
      draw(snapshot);
      setPhase(next);
    },
    paint(now: number) {
      if (!source || phase === "frozen") return;
      const entering = phase === "enter";
      const progress = Math.max(
        0,
        Math.min(1, (now - startedAt) / (entering ? ENTER_MS : RECOVER_MS)),
      );
      const target = captureLoginPose(scene, entering ? "failure" : "normal");
      if (!target) {
        settle(entering);
        return;
      }
      draw(mixFailureSnapshot(source, target, progress * progress * (3 - 2 * progress)));
      if (progress === 1) settle(entering);
    },
    dispose() {
      painter.dispose();
      delete scene.dataset.failurePhase;
      source = null;
      painted = null;
    },
  };
}
