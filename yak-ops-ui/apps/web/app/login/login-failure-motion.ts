import { LOGIN_FAILURE_MOTION_MS } from "./login-interaction";

// Visual feedback ends before the existing form-owned failure window. No timers or state writes.
export const LOGIN_FAILURE_LOCAL_MOTION_MS = LOGIN_FAILURE_MOTION_MS - 100;

export type LoginFailureMotion = {
  complete: boolean;
  purpleX: number;
  purpleRotate: number;
  blackOpen: number;
  orangeOpen: number;
  yellowWave: number;
};

const REST_MOTION: LoginFailureMotion = {
  complete: true,
  purpleX: 0,
  purpleRotate: 0,
  blackOpen: 1,
  orangeOpen: 1,
  yellowWave: 0,
};

function ramp(time: number, start: number, end: number) {
  const value = Math.max(0, Math.min(1, (time - start) / (end - start)));
  return value * value * (3 - 2 * value);
}

function sampleStops(time: number, stops: readonly (readonly [number, number])[]) {
  for (let index = 1; index < stops.length; index += 1) {
    const [start, from] = stops[index - 1];
    const [end, to] = stops[index];
    if (time <= end) return from + (to - from) * ramp(time, start, end);
  }
  return stops[stops.length - 1][1];
}

function eyeOpening(time: number, start: number, closed: number, release: number, end: number) {
  return 1 - 0.92 * ramp(time, start, closed) * (1 - ramp(time, release, end));
}

/** One failure plays once. Invalid/late time returns the exact authored pose, never a backlog. */
export function sampleLoginFailureMotion(elapsedMs: number): LoginFailureMotion {
  if (!Number.isFinite(elapsedMs) || elapsedMs >= LOGIN_FAILURE_LOCAL_MOTION_MS) {
    return REST_MOTION;
  }
  const time = Math.max(0, elapsedMs);
  const purpleX = sampleStops(time, [
    [0, 0],
    [50, 0],
    [135, -12],
    [250, 10],
    [355, -6],
    [470, 3.5],
    [600, 0],
  ]);
  const purpleRotate = sampleStops(time, [
    [0, 0],
    [50, 0],
    [135, 6],
    [250, -3.5],
    [355, 3],
    [470, -1.5],
    [600, 0],
  ]);
  const waveProgress = Math.max(0, Math.min(1, (time - 50) / 550));
  return {
    complete: false,
    purpleX,
    purpleRotate,
    blackOpen: eyeOpening(time, 140, 210, 370, 500),
    orangeOpen: eyeOpening(time, 150, 195, 210, 280) * eyeOpening(time, 330, 375, 390, 460),
    // One signed wave with zero slope at both ends; geometry stays in YellowCharacter.
    yellowWave: Math.sin(2 * Math.PI * waveProgress) * Math.sin(Math.PI * waveProgress) ** 2,
  };
}
