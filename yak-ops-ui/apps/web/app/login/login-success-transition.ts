import {
  captureLoginPose,
  createLoginPosePainter,
  mixLoginPoseSnapshot,
  type LoginPoseSnapshot,
} from "./login-pose-snapshot";

export const LOGIN_SUCCESS_TRANSITION_END = "yak-login-success-transition-end";
const ENTER_MS = 200;
const RECOVER_MS = 280;
type Phase = "none" | "enter" | "hold" | "recover" | "frozen";

// This is only the visual portion of LoginPanel's existing feedback window.
export function sampleLoginSuccessTransition(elapsedMs: number, recovering = false) {
  const elapsed = Number.isNaN(elapsedMs) ? 0 : elapsedMs;
  const progress = Math.max(0, Math.min(1, elapsed / (recovering ? RECOVER_MS : ENTER_MS)));
  return { blend: progress * progress * (3 - 2 * progress), complete: progress === 1 };
}

export function createLoginSuccessTransition(scene: HTMLElement, id: string) {
  const painter = createLoginPosePainter(scene, `${id}-success`, "success");
  let phase: Phase = "none";
  let source: LoginPoseSnapshot | null = null;
  let painted: LoginPoseSnapshot | null = null;
  let startedAt = 0;
  const setPhase = (next: Phase) => {
    phase = next;
    scene.dataset.successPhase = next;
  };
  const settle = (success: boolean) => {
    const wasVisible = phase !== "none";
    setPhase(success ? "hold" : "none");
    source = null;
    painted = null;
    if (wasVisible && !success) scene.dispatchEvent(new Event(LOGIN_SUCCESS_TRANSITION_END));
  };
  return {
    visible: () => phase !== "none",
    captureVisible: () => painted ?? (phase === "hold" ? captureLoginPose(scene, "success") : null),
    settle,
    begin(next: "enter" | "recover" | "frozen", snapshot: LoginPoseSnapshot | null, now: number) {
      if (!snapshot) {
        settle(next === "enter");
        return;
      }
      source = snapshot;
      painted = snapshot;
      startedAt = now;
      painter.draw(snapshot);
      setPhase(next);
    },
    paint(now: number) {
      if (!source || phase === "frozen") return;
      const entering = phase === "enter";
      const target = captureLoginPose(scene, entering ? "success" : "normal");
      if (!target) {
        settle(entering);
        return;
      }
      const { blend, complete } = sampleLoginSuccessTransition(now - startedAt, !entering);
      painted = mixLoginPoseSnapshot(source, target, blend);
      painter.draw(painted);
      if (complete) settle(entering);
    },
    dispose() {
      painter.dispose();
      delete scene.dataset.successPhase;
      source = null;
      painted = null;
    },
  };
}
