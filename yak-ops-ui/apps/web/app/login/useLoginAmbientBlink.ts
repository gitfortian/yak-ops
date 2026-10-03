import { useLayoutEffect, type RefObject } from "react";

import { LOGIN_FAILURE_TRANSITION_END } from "./login-failure-transition";
import { LOGIN_ENTRANCE_COMPLETE_EVENT } from "./login-entrance";
import type { LoginSceneState } from "./login-interaction";

type Character = "purple" | "black" | "yellow" | "orange";
type BlinkSlot = { character: Character; dueAt: number };

const BLINK_CHARACTERS: Record<LoginSceneState, readonly Character[]> = {
  idle: ["purple", "black", "orange"],
  inputFocus: ["purple", "black", "yellow", "orange"],
  passwordVisible: ["purple", "black", "yellow"],
  submitting: [],
  success: [],
  failure: [],
};

function randomBetween(min: number, max: number) {
  return min + Math.random() * (max - min);
}

// Each white eye's existing group owns its white, clipped pupil and local center.
// Black-dot eyes animate directly. Never animate a face or the result-eyes layer.
function getBlinkEyes(scene: HTMLElement, character: Character, state: LoginSceneState) {
  const root = scene.querySelector(`[data-character="${character}"]`);
  if (!root) return [];
  const pose = state === "idle" ? "idle" : state === "passwordVisible" ? "reveal" : "input";
  const selector =
    character === "purple" || character === "black"
      ? `[data-pose-eye="${pose}"]`
      : character === "orange"
        ? ".yak-login-character--orange__eyes-open circle"
        : "[data-yellow-eye]";

  return Array.from(root.querySelectorAll<SVGGraphicsElement>(selector)).flatMap((eye) => {
    const element = character === "purple" || character === "black" ? eye.parentElement : eye;
    const x = Number(eye.getAttribute("cx"));
    const y = Number(eye.getAttribute("cy"));
    if (!element || !Number.isFinite(x) || !Number.isFinite(y)) return [];
    return [{ element, origin: `${x}px ${y}px` }];
  });
}

/** One scene-local wake-up timer; independent character deadlines, no extra RAF or React frames. */
export function useLoginAmbientBlink(
  sceneRef: RefObject<HTMLDivElement | null>,
  sceneState: LoginSceneState,
) {
  // Cancel a half-closed eye during the same commit that changes the authored pose.
  useLayoutEffect(() => {
    const scene = sceneRef.current;
    const characters = BLINK_CHARACTERS[sceneState];
    if (!scene || !characters.length || typeof scene.animate !== "function") return;

    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const animations = new Set<Animation>();
    let slots: BlinkSlot[] = [];
    let timer = 0;
    let generation = 0;
    let disposed = false;
    let available = false;
    const fixedIdle = (character: Character) => sceneState === "idle" && character === "orange";
    // Blue/black idle gaze is random too; orange alone retains its existing fixed cadence.
    const firstDelay = (character: Character) =>
      fixedIdle(character) ? 4600 : randomBetween(1500, 3300);
    const canRun = () =>
      !disposed &&
      !media.matches &&
      !document.hidden &&
      scene.isConnected &&
      scene.dataset.entranceState !== "playing" &&
      (!scene.dataset.failurePhase || scene.dataset.failurePhase === "none") &&
      scene.dataset.sceneState === sceneState &&
      scene.getClientRects().length > 0;

    const stop = () => {
      generation += 1;
      window.clearTimeout(timer);
      slots = [];
      for (const animation of animations) {
        animation.onfinish = null;
        animation.cancel();
      }
      animations.clear();
    };

    function schedule(epoch: number) {
      window.clearTimeout(timer);
      const dueAt = Math.min(...slots.map((slot) => slot.dueAt));
      if (Number.isFinite(dueAt)) {
        timer = window.setTimeout(() => tick(epoch), Math.max(0, dueAt - performance.now()));
      }
    }

    function tick(epoch: number) {
      if (epoch !== generation || disposed) return;
      if (!scene || !canRun()) {
        stop();
        available = false;
        return;
      }
      const now = performance.now();
      for (const slot of slots) {
        if (slot.dueAt > now) continue;
        // A throttled/blocked page must not replay a backlog of overdue blinks.
        if (now - slot.dueAt > 1000) {
          slot.dueAt = now + firstDelay(slot.character);
          continue;
        }
        const eyes = getBlinkEyes(scene, slot.character, sceneState);
        if (!eyes.length) {
          slot.dueAt = now + firstDelay(slot.character);
          continue;
        }
        slot.dueAt = Infinity;
        let remaining = eyes.length;
        const duration = fixedIdle(slot.character) ? 180 : randomBetween(140, 200);
        const startTime = document.timeline.currentTime;
        for (const { element, origin } of eyes) {
          const animation = element.animate(
            [
              { offset: 0, transform: "scaleY(1)" },
              { offset: 0.4, transform: "scaleY(0.1)" },
              { offset: 0.55, transform: "scaleY(0.1)" },
              { offset: 1, transform: "scaleY(1)" },
            ].map((keyframe) => ({
              ...keyframe,
              transformBox: "view-box",
              transformOrigin: origin,
            })),
            { id: `yak-login-ambient-blink-${slot.character}`, duration, easing: "ease-in-out" },
          );
          // Both eyes use exactly the same timeline position, even on a slow frame.
          if (startTime !== null) animation.startTime = startTime;
          animations.add(animation);
          animation.onfinish = () => {
            animation.onfinish = null;
            animations.delete(animation);
            animation.cancel();
            if (epoch !== generation || !canRun()) return;
            remaining -= 1;
            if (remaining === 0) {
              // Only orange idle uses start-to-start cadence; all other slots re-sample after closing.
              slot.dueAt = fixedIdle(slot.character)
                ? now + 4600
                : performance.now() + randomBetween(3500, 8000);
              schedule(epoch);
            }
          };
        }
      }
      schedule(epoch);
    }

    const reset = () => {
      stop();
      available = canRun();
      if (!available) return;
      const now = performance.now();
      slots = characters.map((character) => ({ character, dueAt: now + firstDelay(character) }));
      schedule(generation);
    };
    // Stop when the responsive layout hides the illustration; resizing a visible one does not reset it.
    const observer = new ResizeObserver(() => {
      if (canRun() !== available) reset();
    });
    scene.addEventListener(LOGIN_ENTRANCE_COMPLETE_EVENT, reset);
    scene.addEventListener(LOGIN_FAILURE_TRANSITION_END, reset);
    reset();
    observer.observe(scene);
    media.addEventListener("change", reset);
    document.addEventListener("visibilitychange", reset);
    return () => {
      disposed = true;
      stop();
      observer.disconnect();
      scene.removeEventListener(LOGIN_ENTRANCE_COMPLETE_EVENT, reset);
      scene.removeEventListener(LOGIN_FAILURE_TRANSITION_END, reset);
      media.removeEventListener("change", reset);
      document.removeEventListener("visibilitychange", reset);
    };
  }, [sceneRef, sceneState]);
}
