import { useId, useLayoutEffect, useRef } from "react";

import YellowCharacter, { type YellowCharacterHandle, type YellowPointer } from "./YellowCharacter";
import "./login-characters.css";
import {
  resolveLoginSceneState,
  type LoginFocusState,
  type LoginResultState,
} from "./login-interaction";
import { useLoginAmbientBlink } from "./useLoginAmbientBlink";
import {
  BLACK_DEFAULT_BODY_PATH,
  buildOrangeBodyPath,
  LOGIN_ENTRANCE_COMPLETE_EVENT,
  LOGIN_ENTRANCE_DURATION_MS,
  LOGIN_ENTRANCE_START,
  PURPLE_DEFAULT_BODY_PATH,
  sampleLoginEntrance,
  type LoginEntrancePose,
} from "./login-entrance";

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

// Keep the original 60 Hz response rates, but integrate elapsed time rather than frames.
function smoothMotion(current: number, target: number, rate: number, deltaMs: number) {
  return current + (target - current) * (1 - Math.pow(1 - rate, deltaMs / (1000 / 60)));
}

// Exact underdamped spring step. Velocity is measured per second, not per frame.
function stepOrangeSpring(position: number, velocity: number, target: number, deltaMs: number) {
  const seconds = deltaMs / 1000;
  const frequency = 9;
  const damping = frequency * 0.82;
  const dampedFrequency = frequency * Math.sqrt(1 - 0.82 ** 2);
  const decay = Math.exp(-damping * seconds);
  const cosine = Math.cos(dampedFrequency * seconds);
  const sine = Math.sin(dampedFrequency * seconds);
  const offset = position - target;

  const drift = (velocity + damping * offset) / dampedFrequency;
  const force = (damping * velocity + frequency ** 2 * offset) / dampedFrequency;

  return {
    position: target + decay * (offset * cosine + drift * sine),
    velocity: decay * (velocity * cosine - force * sine),
  };
}

// Input proportions remain independent of the entrance's temporary bending path.
function buildBlackBodyPath(inputMix = 0) {
  return inputMix > 0
    ? `M${svgPoint(342 + inputMix * 6)} 550V${svgPoint(242 - inputMix * 9)}H${svgPoint(464 + inputMix * 12)}V550Z`
    : BLACK_DEFAULT_BODY_PATH;
}

function svgPoint(value: number) {
  return Number(value.toFixed(2));
}

// Resolve against the resting face in the root SVG, never against animated face bounds.
function resolveOrangePointerTarget(svg: SVGSVGElement | null, pointer: YellowPointer | null) {
  const rest = { x: 0, y: 0 };
  if (!svg || !pointer || !svg.getClientRects().length) return rest;
  const matrix = svg.getScreenCTM();
  if (!matrix) return rest;
  const determinant = matrix.a * matrix.d - matrix.b * matrix.c;
  if (!Number.isFinite(determinant) || Math.abs(determinant) < 1e-8) return rest;
  const point = new DOMPoint(pointer.x, pointer.y).matrixTransform(matrix.inverse());
  const dx = point.x - 230;
  const dy = point.y - 460;
  if (!Number.isFinite(dx) || !Number.isFinite(dy)) return rest;
  return { x: dx / Math.hypot(dx, 180), y: dy / Math.hypot(dy, 160) };
}

function resolveOrangeFacePose(x: number, y: number) {
  const horizontal = clamp(x, -1, 1);
  const vertical = clamp(y, -1, 1);
  return {
    faceX: horizontal * 68,
    faceY: vertical * (28 - Math.abs(horizontal) * 12) - Math.abs(horizontal) * 6,
    // Left gaze lifts the left eye; right gaze lifts the right. Rotate the whole smile.
    faceRotate: -horizontal * 11,
  };
}

// Both animated and reduced-motion inputs use the same authored body endpoints.
function buildPurpleBodyPath(inputMix: number) {
  return inputMix > 0
    ? `M${svgPoint(212 - inputMix * 9)} 550V102H${svgPoint(404 + inputMix * 4)}V550Z`
    : PURPLE_DEFAULT_BODY_PATH;
}

// Ordinary gaze has its own fixed anchors; authored input/reveal geometry stays separate.
const POINTER_EYES = {
  purple: { x: 304.5, y: 145, halfGap: 30.5, radius: 5.5, pupilRadius: 2.3 },
  black: { x: 403.5, y: 278, halfGap: 24.5, radius: 8.5, pupilRadius: 3.4 },
} as const;
type PointerCharacter = keyof typeof POINTER_EYES;

function resolvePointerEyeTarget(
  svg: SVGSVGElement | null,
  pointer: YellowPointer | null,
  character: PointerCharacter,
) {
  const rest = { x: 0, y: 0 };
  if (!svg || !pointer || !svg.getClientRects().length) return rest;
  const matrix = svg.getScreenCTM();
  if (!matrix) return rest;
  const determinant = matrix.a * matrix.d - matrix.b * matrix.c;
  if (!Number.isFinite(determinant) || Math.abs(determinant) < 1e-8) return rest;
  const point = new DOMPoint(pointer.x, pointer.y).matrixTransform(matrix.inverse());
  const anchor = POINTER_EYES[character];
  const dx = point.x - anchor.x;
  const dy = point.y - anchor.y;
  if (!Number.isFinite(dx) || !Number.isFinite(dy)) return rest;
  return { x: dx / Math.hypot(dx, 160), y: dy / Math.hypot(dy, 140) };
}

// Rigid face travel preserves eye spacing. Only the pupil moves inside each clipped white.
function resolvePointerEyePose(character: PointerCharacter, x: number, y: number) {
  const horizontal = clamp(x, -1, 1);
  const vertical = clamp(y, -1, 1);
  const purple = character === "purple";
  const rotate = horizontal * (purple ? 3.5 : -4);
  const radians = (rotate * Math.PI) / 180;
  const norm = Math.max(1, Math.hypot(horizontal, vertical));
  const { radius, pupilRadius } = POINTER_EYES[character];
  const travel = radius - pupilRadius - 0.2;
  // Counter-rotate the gaze vector so a rolled face still looks toward the actual pointer.
  return {
    x: horizontal * (purple ? 50 : 25),
    y: vertical * (purple ? 24 : 18),
    rotate,
    pupilX: ((horizontal * Math.cos(radians) + vertical * Math.sin(radians)) / norm) * travel,
    pupilY: ((vertical * Math.cos(radians) - horizontal * Math.sin(radians)) / norm) * travel,
  };
}

function PointerEyes({ character }: { character: PointerCharacter }) {
  const clipId = useId();
  const { x, y, halfGap, radius, pupilRadius } = POINTER_EYES[character];
  return (
    <g className={`yak-login-character--${character}__eyes`}>
      {[x - halfGap, x + halfGap].map((cx, index) => (
        <g key={cx}>
          <defs>
            <clipPath id={`${clipId}-idle-${index}`} clipPathUnits="userSpaceOnUse">
              <circle cx={cx} cy={y} r={radius} />
            </clipPath>
          </defs>
          <circle data-pose-eye="idle" cx={cx} cy={y} r={radius} fill="#FFFFFF" />
          <g clipPath={`url(#${clipId}-idle-${index})`}>
            <circle
              data-pose-pupil="idle"
              className={`yak-login-character--${character}__pupil`}
              cx={cx}
              cy={y}
              r={pupilRadius}
              fill="#171717"
            />
          </g>
        </g>
      ))}
    </g>
  );
}

// Authored gaze is independent of face position and ordinary pointer tracking.
function CharacterPoseEyes({
  character,
  pose,
}: {
  character: "purple" | "black";
  pose: "input" | "reveal" | "failure";
}) {
  const clipId = useId();
  const purple = character === "purple";
  const input = pose === "input";
  const failure = pose === "failure";
  const centers = failure
    ? purple
      ? [300, 360]
      : [404, 437]
    : purple
      ? [274, input ? 335 : 331]
      : [379, 411];
  const y = failure ? (purple ? 148 : 355) : purple ? 145 : 278;
  const rx = purple ? 5.5 : 9;
  const ry = failure ? (purple ? 6 : 8.5) : input ? rx : purple ? 6 : 10;

  return (
    <g className={`yak-login-character__${pose}-eyes`}>
      {centers.map((x, index) => {
        let pupilX = x + (input ? 2.8 : purple ? 3 : 1.5);
        let pupilY = y - (input || purple ? 0 : 5);
        let pupilRadius = input ? (purple ? 2.5 : 4.2) : purple ? 3.2 : 7;
        if (failure) {
          // The shortened black character glances upward with an asymmetric white crescent.
          pupilX = x + (purple ? 3 : index === 0 ? 1 : 4.5);
          pupilY = y - (purple ? 0 : index === 0 ? 1.5 : 4);
          pupilRadius = purple ? 3.2 : index === 0 ? 5.8 : 7;
        }
        return (
          <g key={x}>
            <defs>
              <clipPath id={`${clipId}-${index}`} clipPathUnits="userSpaceOnUse">
                <ellipse cx={x} cy={y} rx={rx} ry={ry} />
              </clipPath>
            </defs>
            <ellipse data-pose-eye={pose} cx={x} cy={y} rx={rx} ry={ry} fill="#FFFFFF" />
            <g clipPath={`url(#${clipId}-${index})`}>
              <circle
                data-pose-pupil={pose}
                cx={pupilX}
                cy={pupilY}
                r={pupilRadius}
                fill="#171717"
              />
            </g>
          </g>
        );
      })}
    </g>
  );
}

// Failure is a mutually exclusive, static drawing outside the motion rig.
// Hidden branches use display:none so they cannot shift the other states' SVG bounds.
function PurpleCharacter() {
  return (
    <g data-character="purple" className="yak-login-character yak-login-character--purple">
      <g
        data-character-motion
        className="yak-login-character--purple__entry"
        transform={LOGIN_ENTRANCE_START.purple.transform}
      >
        <g className="yak-login-character--purple__breathe">
          <g className="yak-login-character--purple__result">
            <g className="yak-login-character--purple__focus">
              <g className="yak-login-character--purple__body">
                <path data-purple-body-path d={LOGIN_ENTRANCE_START.purple.path} fill="#6128F5" />
                <g
                  data-entrance-face="purple"
                  transform={LOGIN_ENTRANCE_START.purple.faceTransform}
                  opacity={LOGIN_ENTRANCE_START.purple.faceOpacity}
                >
                  <g className="yak-login-character--purple__face">
                    <g className="yak-login-character--purple__pointer-face">
                      <g className="yak-login-character--purple__state-face-pose">
                        <g className="yak-login-character--purple__result-eyes">
                          <g className="yak-login-character--purple__focus-eyes">
                            <PointerEyes character="purple" />
                            <CharacterPoseEyes character="purple" pose="input" />
                            <CharacterPoseEyes character="purple" pose="reveal" />
                          </g>
                        </g>
                        <ellipse
                          className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--purple__mouth--idle"
                          cx="304.5"
                          cy="174"
                          rx="7.5"
                          ry="4.2"
                          fill="#171717"
                        />
                        <ellipse
                          className="yak-login-character__mouth yak-login-character--purple__mouth--input"
                          cx="304.5"
                          cy="158"
                          rx="4"
                          ry="16"
                          fill="#171717"
                        />
                        <path
                          className="yak-login-character__mouth yak-login-character__mouth--reveal"
                          d="M292 169Q304.5 157 317 169"
                          fill="none"
                          stroke="#171717"
                          strokeWidth="5"
                          strokeLinecap="round"
                        />
                        <path
                          className="yak-login-character__mouth yak-login-character__mouth--success"
                          d="M292 169Q304.5 187 317 169"
                          fill="none"
                          stroke="#171717"
                          strokeWidth="3"
                          strokeLinecap="round"
                        />
                      </g>
                    </g>
                  </g>
                </g>
              </g>
            </g>
          </g>
        </g>
      </g>
      <g className="yak-login-character__failure-pose" data-failure-pose="purple">
        <path
          data-failure-body
          d="M212 550L220 377L168 250L206 74L410 117L386 231L418 303L404 550Z"
          fill="#6128F5"
        />
        <g data-failure-face transform="rotate(4 330 148)">
          <CharacterPoseEyes character="purple" pose="failure" />
          <path
            data-failure-mouth
            d="M319 175Q330 164 341 175"
            fill="none"
            stroke="#171717"
            strokeWidth="5"
            strokeLinecap="round"
          />
        </g>
      </g>
    </g>
  );
}

function BlackCharacter() {
  return (
    <g data-character="black" className="yak-login-character yak-login-character--black">
      <g
        data-character-motion
        className="yak-login-character--black__entry"
        transform={LOGIN_ENTRANCE_START.black.transform}
      >
        <g className="yak-login-character--black__breathe">
          <g className="yak-login-character--black__result">
            <g className="yak-login-character--black__focus">
              <g className="yak-login-character--black__body">
                <path data-black-body-path d={LOGIN_ENTRANCE_START.black.path} fill="#191A20" />
                <g
                  data-entrance-face="black"
                  transform={LOGIN_ENTRANCE_START.black.faceTransform}
                  opacity={LOGIN_ENTRANCE_START.black.faceOpacity}
                >
                  <g className="yak-login-character--black__face">
                    <g className="yak-login-character--black__pointer-face">
                      <g className="yak-login-character--black__state-face-pose">
                        <g className="yak-login-character--black__result-eyes">
                          <g className="yak-login-character--black__focus-eyes">
                            <PointerEyes character="black" />
                            <CharacterPoseEyes character="black" pose="input" />
                            <CharacterPoseEyes character="black" pose="reveal" />
                          </g>
                        </g>
                        <path
                          className="yak-login-character__mouth yak-login-character__mouth--success"
                          d="M391 309Q403.5 324 416 309"
                          fill="none"
                          stroke="#FFFFFF"
                          strokeWidth="3"
                          strokeLinecap="round"
                        />
                      </g>
                    </g>
                  </g>
                </g>
              </g>
            </g>
          </g>
        </g>
      </g>
      <g className="yak-login-character__failure-pose" data-failure-pose="black">
        <path data-failure-body d="M354 550V305H482V550Z" fill="#191A20" />
        <CharacterPoseEyes character="black" pose="failure" />
      </g>
    </g>
  );
}

function OrangeCharacter() {
  return (
    <g data-character="orange" className="yak-login-character yak-login-character--orange">
      <g
        data-character-motion
        className="yak-login-character--orange__entry"
        transform={LOGIN_ENTRANCE_START.orange.transform}
      >
        <g className="yak-login-character--orange__breathe">
          <g className="yak-login-character--orange__result">
            <g className="yak-login-character--orange__focus">
              <g className="yak-login-character--orange__body">
                <path data-orange-body-path d={LOGIN_ENTRANCE_START.orange.path} fill="#FF7D2A" />
                <g
                  data-entrance-face="orange"
                  transform={LOGIN_ENTRANCE_START.orange.faceTransform}
                  opacity={LOGIN_ENTRANCE_START.orange.faceOpacity}
                >
                  <g className="yak-login-character--orange__face">
                    <g className="yak-login-character--orange__state-face-pose">
                      <g className="yak-login-character--orange__result-eyes">
                        <g className="yak-login-character--orange__focus-eyes">
                          <g className="yak-login-character--orange__eyes">
                            <g className="yak-login-character--orange__eyes-open">
                              <circle cx="190" cy="460" r="8" fill="#171717" />
                              <circle cx="270" cy="460" r="8" fill="#171717" />
                            </g>
                            <g className="yak-login-character--orange__eyes-blink">
                              <path
                                d="M181 461Q190 452 199 461"
                                fill="none"
                                stroke="#171717"
                                strokeWidth="4"
                                strokeLinecap="round"
                              />
                              <path
                                d="M261 461Q270 452 279 461"
                                fill="none"
                                stroke="#171717"
                                strokeWidth="4"
                                strokeLinecap="round"
                              />
                            </g>
                          </g>
                        </g>
                      </g>
                      <g className="yak-login-character--orange__mouth-pose">
                        <path
                          className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--happy"
                          d="M213 475Q213 473 215 473H246Q248 473 248 475C246.8 483.5 240 488 230.5 488C221 488 214.2 483.5 213 475Z"
                          fill="#171717"
                        />
                        <circle
                          className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--input"
                          cx="231"
                          cy="478"
                          r="7"
                          fill="#171717"
                        />
                        <path
                          className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--password"
                          d="M222 473Q231 464 240 473"
                          fill="none"
                          stroke="#171717"
                          strokeWidth="4"
                          strokeLinecap="round"
                        />
                        <path
                          className="yak-login-character__mouth yak-login-character__mouth--success"
                          d="M208 481Q208 479 210.5 479H250.5Q253 479 253 481C251 495 242.5 504 230.5 504C218.5 504 210 495 208 481Z"
                          fill="#171717"
                        />
                      </g>
                    </g>
                  </g>
                </g>
              </g>
            </g>
          </g>
        </g>
      </g>
      <g className="yak-login-character__failure-pose" data-failure-pose="orange">
        <path data-failure-body d={buildOrangeBodyPath(0, 0)} fill="#FF7D2A" />
        <g data-failure-face transform="rotate(6 310 500)">
          <circle data-pose-eye="failure" cx="270" cy="500" r="8" fill="#171717" />
          <circle data-pose-eye="failure" cx="350" cy="500" r="8" fill="#171717" />
          <path
            data-failure-mouth
            d="M298 526Q310 514 322 526"
            fill="none"
            stroke="#171717"
            strokeWidth="4"
            strokeLinecap="round"
          />
        </g>
      </g>
    </g>
  );
}

interface LoginCharactersProps {
  focusState: LoginFocusState;
  resultState: LoginResultState;
  passwordVisible: boolean;
}

export default function LoginCharacters({
  focusState,
  resultState,
  passwordVisible,
}: LoginCharactersProps) {
  const sceneRef = useRef<HTMLDivElement | null>(null);
  const yellowRef = useRef<YellowCharacterHandle | null>(null);
  const sceneState = resolveLoginSceneState(focusState, resultState, passwordVisible);
  const sceneStateRef = useRef(sceneState);
  const entranceRef = useRef<{ startedAt: number | null; complete: boolean }>({
    startedAt: null,
    complete: false,
  });
  const finishEntranceRef = useRef<(() => void) | null>(null);
  useLoginAmbientBlink(sceneRef, sceneState);

  useLayoutEffect(() => {
    sceneStateRef.current = sceneState;
    if (sceneState !== "idle") finishEntranceRef.current?.();
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const applyReducedScenePose = () => {
      if (media.matches) {
        const input = sceneState === "inputFocus";
        sceneRef.current
          ?.querySelector("[data-purple-body-path]")
          ?.setAttribute("d", buildPurpleBodyPath(input ? 1 : 0));
        sceneRef.current
          ?.querySelector("[data-black-body-path]")
          ?.setAttribute("d", buildBlackBodyPath(input ? 1 : 0));
        sceneRef.current
          ?.querySelector("[data-orange-body-path]")
          ?.setAttribute("d", buildOrangeBodyPath(0, 0, input ? 1 : 0));
        sceneRef.current?.style.setProperty(
          "--yak-input-mix",
          sceneState === "inputFocus" ? "1" : "0",
        );
        sceneRef.current?.style.setProperty(
          "--yak-reveal-mix",
          sceneState === "passwordVisible" ? "1" : "0",
        );
      }
    };
    applyReducedScenePose();
    media.addEventListener("change", applyReducedScenePose);
    return () => media.removeEventListener("change", applyReducedScenePose);
  }, [sceneState]);

  useLayoutEffect(() => {
    const scene = sceneRef.current;
    if (!scene) return;

    let purpleX = 0;
    let purpleY = 0;
    let inputMix = 0;
    let revealMix = 0;
    let blackX = 0;
    let blackY = 0;
    let orangeX = 0;
    let orangeY = 0;
    let orangeVelocityX = 0;
    let orangeVelocityY = 0;
    let orangeBodyX = 0;
    let orangeBodyY = 0;
    let pointerPosition: YellowPointer | null = null;
    let frame = 0;
    if (entranceRef.current.startedAt === null) entranceRef.current.startedAt = performance.now();
    const startedAt = entranceRef.current.startedAt;
    let previousFrameAt = performance.now();

    const purpleBodyPath = scene.querySelector<SVGPathElement>("[data-purple-body-path]");
    const blackBodyPath = scene.querySelector<SVGPathElement>("[data-black-body-path]");
    const svg = scene.querySelector<SVGSVGElement>("svg");
    const orangeBodyPath = scene.querySelector<SVGPathElement>("[data-orange-body-path]");
    if (!purpleBodyPath || !blackBodyPath || !orangeBodyPath) return;

    const entryNodes = (["purple", "black", "orange"] as const).map((character) => ({
      character,
      body: scene.querySelector<SVGPathElement>(`[data-${character}-body-path]`),
      entry: scene.querySelector<SVGGElement>(`.yak-login-character--${character}__entry`),
      face: scene.querySelector<SVGGElement>(`[data-entrance-face="${character}"]`),
    }));
    const paintEntrance = (pose: LoginEntrancePose) => {
      for (const { character, body, entry, face } of entryNodes) {
        const value = pose[character];
        body?.setAttribute("d", value.path);
        entry?.setAttribute("transform", value.transform);
        face?.setAttribute("transform", value.faceTransform);
        face?.setAttribute("opacity", String(value.faceOpacity));
      }
      yellowRef.current?.setEntrance(pose.yellow);
    };
    const finishEntrance = () => {
      // Also repaint after StrictMode effect setup, without starting a second entrance.
      paintEntrance(sampleLoginEntrance(LOGIN_ENTRANCE_DURATION_MS));
      yellowRef.current?.setEntrance(null);
      const wasPlaying = scene.dataset.entranceState !== "complete";
      entranceRef.current.complete = true;
      scene.dataset.entranceState = "complete";
      if (wasPlaying) scene.dispatchEvent(new Event(LOGIN_ENTRANCE_COMPLETE_EVENT));
    };
    finishEntranceRef.current = () => {
      if (!entranceRef.current.complete) finishEntrance();
    };

    const clearPointerPosition = () => {
      pointerPosition = null;
    };
    const handlePointerOut = (event: PointerEvent) => {
      if (!event.relatedTarget) clearPointerPosition();
    };
    const handleVisibilityChange = () => {
      if (document.hidden) {
        clearPointerPosition();
        if (!entranceRef.current.complete) finishEntrance();
      }
    };

    const handlePointerMove = (event: PointerEvent) => {
      pointerPosition =
        event.pointerType === "touch" ? null : { x: event.clientX, y: event.clientY };
    };

    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const animate = (now: number) => {
      frame = 0;
      if (motionPreference.matches) {
        handleMotionPreference();
        return;
      }
      const elapsedMs = Math.max(0, now - previousFrameAt);
      previousFrameAt = now;
      // A suspended tab must not integrate seconds of old spring velocity on resume.
      const deltaMs = elapsedMs > 250 ? 0 : Math.min(elapsedMs, 64);
      if (elapsedMs > 250) {
        orangeVelocityX = 0;
        orangeVelocityY = 0;
      }
      const activeSceneState = sceneStateRef.current;
      // One clock blends mutually exclusive authored poses. Field-to-field focus is unchanged.
      const inputTarget = activeSceneState === "inputFocus" ? 1 : 0;
      const revealTarget = activeSceneState === "passwordVisible" ? 1 : 0;
      inputMix = smoothMotion(inputMix, inputTarget, 0.2, deltaMs);
      revealMix = smoothMotion(revealMix, revealTarget, 0.2, deltaMs);
      if (Math.abs(inputMix - inputTarget) < 0.001) inputMix = inputTarget;
      if (Math.abs(revealMix - revealTarget) < 0.001) revealMix = revealTarget;
      scene.style.setProperty("--yak-input-mix", String(inputMix));
      scene.style.setProperty("--yak-reveal-mix", String(revealMix));
      const freePoseWeight = Math.max(0, 1 - inputMix - revealMix);
      const pointerWeight = freePoseWeight;
      if (!entranceRef.current.complete) {
        const pose = sampleLoginEntrance(now - startedAt);
        if (pose.complete || activeSceneState !== "idle") finishEntrance();
        else paintEntrance(pose);
      }
      const entering = !entranceRef.current.complete;
      if (!entering) {
        purpleBodyPath.setAttribute("d", buildPurpleBodyPath(inputMix));
        blackBodyPath.setAttribute("d", buildBlackBodyPath(inputMix));
      }
      const idlePointer = !entering && activeSceneState === "idle" ? pointerPosition : null;
      const purpleTarget = resolvePointerEyeTarget(svg, idlePointer, "purple");
      const blackTarget = resolvePointerEyeTarget(svg, idlePointer, "black");
      purpleX = smoothMotion(purpleX, purpleTarget.x, 0.12, deltaMs);
      purpleY = smoothMotion(purpleY, purpleTarget.y, 0.12, deltaMs);
      blackX = smoothMotion(blackX, blackTarget.x, 0.12, deltaMs);
      blackY = smoothMotion(blackY, blackTarget.y, 0.12, deltaMs);

      const orangeTarget = resolveOrangePointerTarget(svg, idlePointer);
      const orangeTargetX = orangeTarget.x;
      const orangeTargetY = orangeTarget.y;

      const orangeSpringX = stepOrangeSpring(orangeX, orangeVelocityX, orangeTargetX, deltaMs);
      const orangeSpringY = stepOrangeSpring(orangeY, orangeVelocityY, orangeTargetY, deltaMs);
      orangeX = orangeSpringX.position;
      orangeY = orangeSpringY.position;
      orangeVelocityX = orangeSpringX.velocity;
      orangeVelocityY = orangeSpringY.velocity;
      orangeBodyX = smoothMotion(orangeBodyX, orangeTargetX, 0.045, deltaMs);
      orangeBodyY = smoothMotion(orangeBodyY, orangeTargetY, 0.04, deltaMs);

      const purplePose = resolvePointerEyePose("purple", purpleX, purpleY);
      const blackPose = resolvePointerEyePose("black", blackX, blackY);
      scene.style.setProperty("--yak-purple-face-x", `${purplePose.x * pointerWeight}px`);
      scene.style.setProperty("--yak-purple-face-y", `${purplePose.y * pointerWeight}px`);
      scene.style.setProperty(
        "--yak-purple-gaze-rotate",
        `${purplePose.rotate * pointerWeight}deg`,
      );
      scene.style.setProperty("--yak-purple-pupil-x", `${purplePose.pupilX * pointerWeight}px`);
      scene.style.setProperty("--yak-purple-pupil-y", `${purplePose.pupilY * pointerWeight}px`);

      scene.style.setProperty("--yak-black-face-x", `${blackPose.x * pointerWeight}px`);
      scene.style.setProperty("--yak-black-face-y", `${blackPose.y * pointerWeight}px`);
      scene.style.setProperty("--yak-black-face-rotate", "0deg");
      scene.style.setProperty("--yak-black-gaze-rotate", `${blackPose.rotate * pointerWeight}deg`);
      scene.style.setProperty("--yak-black-pupil-x", `${blackPose.pupilX * pointerWeight}px`);
      scene.style.setProperty("--yak-black-pupil-y", `${blackPose.pupilY * pointerWeight}px`);

      if (!entering) {
        orangeBodyPath.setAttribute(
          "d",
          buildOrangeBodyPath(orangeBodyX * freePoseWeight, orangeBodyY * freePoseWeight, inputMix),
        );
      }

      const orangeFacePose = resolveOrangeFacePose(orangeX, orangeY);
      scene.style.setProperty("--yak-orange-face-x", `${orangeFacePose.faceX * pointerWeight}px`);
      scene.style.setProperty("--yak-orange-face-y", `${orangeFacePose.faceY * pointerWeight}px`);
      scene.style.setProperty(
        "--yak-orange-face-rotate",
        `${orangeFacePose.faceRotate * pointerWeight}deg`,
      );

      if (!entering) {
        yellowRef.current?.update(pointerPosition, activeSceneState, deltaMs, inputMix);
      }

      frame = window.requestAnimationFrame(animate);
    };

    const handleMotionPreference = () => {
      window.cancelAnimationFrame(frame);
      frame = 0;
      previousFrameAt = performance.now();
      pointerPosition = null;
      purpleX = 0;
      purpleY = 0;
      blackX = 0;
      blackY = 0;
      orangeX = 0;
      orangeY = 0;
      orangeVelocityX = 0;
      orangeVelocityY = 0;
      orangeBodyX = 0;
      orangeBodyY = 0;
      inputMix = sceneStateRef.current === "inputFocus" ? 1 : 0;
      revealMix = sceneStateRef.current === "passwordVisible" ? 1 : 0;
      scene.style.setProperty("--yak-input-mix", String(inputMix));
      scene.style.setProperty("--yak-reveal-mix", String(revealMix));
      for (const character of ["purple", "black", "orange"]) {
        scene.style.setProperty(`--yak-${character}-face-x`, "0px");
        scene.style.setProperty(`--yak-${character}-face-y`, "0px");
      }
      for (const character of ["purple", "black"]) {
        scene.style.setProperty(`--yak-${character}-gaze-rotate`, "0deg");
        scene.style.setProperty(`--yak-${character}-pupil-x`, "0px");
        scene.style.setProperty(`--yak-${character}-pupil-y`, "0px");
      }
      scene.style.setProperty("--yak-black-face-rotate", "0deg");
      scene.style.setProperty("--yak-orange-face-rotate", "0deg");
      if (motionPreference.matches) {
        // Media changes must not restart entrance or retain a stale pointer on return.
        finishEntrance();
        purpleBodyPath.setAttribute("d", buildPurpleBodyPath(inputMix));
        blackBodyPath.setAttribute("d", buildBlackBodyPath(inputMix));
        orangeBodyPath.setAttribute("d", buildOrangeBodyPath(0, 0, inputMix));
      } else {
        frame = window.requestAnimationFrame(animate);
      }
    };

    const resizeObserver = new ResizeObserver(() => {
      if (!scene.getClientRects().length && !entranceRef.current.complete) {
        pointerPosition = null;
        finishEntrance();
      }
    });
    resizeObserver.observe(scene);
    if (
      entranceRef.current.complete ||
      sceneStateRef.current !== "idle" ||
      document.hidden ||
      !scene.getClientRects().length
    ) {
      finishEntrance();
    } else {
      paintEntrance(sampleLoginEntrance(performance.now() - startedAt));
    }

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("pointerout", handlePointerOut);
    window.addEventListener("blur", clearPointerPosition);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    motionPreference.addEventListener("change", handleMotionPreference);
    if (motionPreference.matches) handleMotionPreference();
    else frame = window.requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerout", handlePointerOut);
      window.removeEventListener("blur", clearPointerPosition);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      motionPreference.removeEventListener("change", handleMotionPreference);
      window.cancelAnimationFrame(frame);
      resizeObserver.disconnect();
      finishEntranceRef.current = null;
    };
  }, []);

  const sceneClass = {
    idle: "",
    inputFocus: "is-input-focus",
    passwordVisible: "is-password-focus is-password-visible",
    submitting: "is-login-submitting",
    failure: "is-login-failure",
    success: "is-login-success",
  }[sceneState];

  return (
    <div
      ref={sceneRef}
      className={`yak-login-characters ${sceneClass} relative min-h-screen overflow-hidden bg-[#efedf2]`}
      data-scene-state={sceneState}
      data-entrance-state="playing"
      aria-hidden="true"
    >
      {/* The SVG bottom is the shared ground; height also limits scale on short screens. */}
      <svg
        className="yak-login-characters__svg absolute bottom-[23%] left-1/2 block h-auto w-[min(88%,820px,92svh)] -translate-x-1/2"
        viewBox="0 0 720 550"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <PurpleCharacter />
        <BlackCharacter />
        <YellowCharacter ref={yellowRef} sceneState={sceneState} />
        <OrangeCharacter />
      </svg>
    </div>
  );
}
