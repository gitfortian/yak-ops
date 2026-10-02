import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";

import YellowCharacter, { type YellowCharacterHandle, type YellowPointer } from "./YellowCharacter";
import "./login-characters.css";
import {
  resolveLoginSceneState,
  type LoginFocusState,
  type LoginResultState,
} from "./login-interaction";

// Entrance, idle deformation and reduced motion share the same resting crown.
const ORANGE_BODY_TOP_Y = 376;

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

function sampleMotionStops(progress: number, stops: readonly (readonly [number, number])[]) {
  for (let index = 1; index < stops.length; index += 1) {
    const [end, to] = stops[index];
    const [start, from] = stops[index - 1];
    if (progress <= end) return lerp(from, to, smoothstep((progress - start) / (end - start)));
  }
  return stops[stops.length - 1][1];
}

// Each edge uses the same bend, so the column keeps its width and fixed ground anchors.
function buildBlackBodyPath(bend: number, inputMix = 0) {
  if (inputMix > 0) {
    return `M${svgPoint(342 + inputMix * 6)} 550V${svgPoint(242 - inputMix * 9)}H${svgPoint(464 + inputMix * 12)}V550Z`;
  }
  if (bend === 0) return "M342 550V242H464V550Z";
  return [
    "M342 550",
    `C342 448 ${svgPoint(342 + bend * 0.45)} 345 ${svgPoint(342 + bend)} 242`,
    `H${svgPoint(464 + bend)}`,
    `C${svgPoint(464 + bend * 0.45)} 345 464 448 464 550`,
    "Z",
  ].join(" ");
}

const BLACK_ENTRY_BEND_STOPS = [
  [0, 0],
  [0.26, -64],
  [0.44, -28],
  [0.5, 38],
  [0.66, -16],
  [0.82, 6],
  [1, 0],
] as const;

function svgPoint(value: number) {
  return Number(value.toFixed(2));
}

function resolveOrangeFacePose(x: number, y: number, pointerPoseEnabled: boolean) {
  if (!pointerPoseEnabled) {
    return {
      faceX: x * 34,
      faceY: y * 14,
      eyeX: x * 6,
      eyeY: y * 3,
      eyeScale: 1,
      mouthRotate: 0,
    };
  }

  const horizontal = clamp(x, -1, 1);
  const vertical = clamp(y, -1, 1);
  const edgeLift = Math.abs(horizontal) * (5 + Math.max(0, -vertical) * 6);

  return {
    faceX: horizontal * 48,
    faceY: vertical * 19 - edgeLift,
    eyeX: horizontal * 3,
    eyeY: vertical * 1.5,
    eyeScale: 1 - Math.max(-horizontal, 0) * 0.22,
    mouthRotate: horizontal * 7,
  };
}

function buildOrangeBodyPath(x: number, y: number, activity: number, inputMix = 0) {
  const topY = ORANGE_BODY_TOP_Y - inputMix * 8;
  const horizontal = clamp(x, -1, 1);
  const vertical = clamp(y, -1, 1);
  const motion = clamp(activity, 0, 1);

  const pointerLift = Math.max(0, -vertical) * 14;
  const motionLift = motion * 8;
  const sideLift = Math.abs(horizontal) * 5;
  const crownX = 244 + horizontal * 18;
  const crownY = topY - pointerLift - motionLift - sideLift;
  const sideY = svgPoint(topY + (550 - topY) * 0.42);
  const leftLift = Math.max(-horizontal, 0) * 14 + Math.max(0, -vertical) * 7 + motion * 4;
  const rightLift = Math.max(horizontal, 0) * 14 + Math.max(0, -vertical) * 7 + motion * 4;

  return [
    "M65 550",
    `C65 ${sideY} ${svgPoint(142 + horizontal * 6)} ${svgPoint(topY - leftLift)}`,
    `${svgPoint(crownX)} ${svgPoint(crownY)}`,
    `C${svgPoint(346.05 + horizontal * 6)} ${svgPoint(topY - rightLift)} 415 ${sideY} 415 550`,
    "Z",
  ].join(" ");
}
function lerp(start: number, end: number, progress: number) {
  return start + (end - start) * progress;
}

function smoothstep(value: number) {
  const progress = clamp(value, 0, 1);
  return progress * progress * (3 - 2 * progress);
}

function quadraticBezier(start: number, control: number, end: number, progress: number) {
  const inverse = 1 - progress;
  return inverse * inverse * start + 2 * inverse * progress * control + progress * progress * end;
}

function buildOrangeShapePath(
  leftX: number,
  rightX: number,
  topX: number,
  topY: number,
  bottomY: number,
  bottomRound: number,
) {
  const width = rightX - leftX;
  const sideY = topY + (bottomY - topY) * 0.42;

  return [
    `M${svgPoint(leftX)} ${svgPoint(bottomY)}`,
    `C${svgPoint(leftX)} ${svgPoint(sideY)}`,
    `${svgPoint(leftX + width * 0.22)} ${svgPoint(topY)}`,
    `${svgPoint(topX)} ${svgPoint(topY)}`,
    `C${svgPoint(rightX - width * 0.197)} ${svgPoint(topY)}`,
    `${svgPoint(rightX)} ${svgPoint(sideY)}`,
    `${svgPoint(rightX)} ${svgPoint(bottomY)}`,
    `C${svgPoint(rightX)} ${svgPoint(bottomY + bottomRound)}`,
    `${svgPoint(leftX)} ${svgPoint(bottomY + bottomRound)}`,
    `${svgPoint(leftX)} ${svgPoint(bottomY)}`,
    "Z",
  ].join(" ");
}

function buildOrangeEntrancePath(progress: number) {
  const value = clamp(progress, 0, 1);

  if (value <= 0.42) {
    // Travel continuously into impact; easing to rest here makes the landing float.
    const phase = value / 0.42;
    const centerX = quadraticBezier(92, 124, 232, phase);
    const centerY = quadraticBezier(520, 320, 520, phase);
    const width = lerp(30, 84, phase);
    const height = lerp(54, 34, phase);
    const topX = centerX + Math.sin(phase * Math.PI) * 6;
    const topY = centerY - height / 2;
    const bottomY = centerY + height / 2;

    return buildOrangeShapePath(
      centerX - width / 2,
      centerX + width / 2,
      topX,
      topY,
      bottomY,
      lerp(10, 3, phase),
    );
  }

  if (value <= 0.54) {
    const phase = smoothstep((value - 0.42) / 0.12);
    const centerX = lerp(232, 244, phase);
    const width = lerp(84, 160, phase);
    const topY = lerp(503, 530, phase);
    const bottomY = lerp(537, 550, phase);

    return buildOrangeShapePath(
      centerX - width / 2,
      centerX + width / 2,
      lerp(232, 244, phase),
      topY,
      bottomY,
      lerp(3, 0, phase),
    );
  }

  if (value <= 0.7) {
    const phase = smoothstep((value - 0.54) / 0.16);
    const bounce = Math.sin(phase * Math.PI) * 30;
    const width = lerp(160, 220, phase);
    const height = lerp(20, 85, phase);
    const bottomY = 550 - bounce;

    return buildOrangeShapePath(
      244 - width / 2,
      244 + width / 2,
      244,
      bottomY - height,
      bottomY,
      Math.sin(phase * Math.PI) * 8,
    );
  }

  // Grow out of the rebound, overshoot once, then settle onto the PR1 resting shape.
  const leftX = sampleMotionStops(value, [
    [0.7, 134],
    [0.86, 62],
    [0.94, 66],
    [1, 65],
  ]);
  const rightX = sampleMotionStops(value, [
    [0.7, 354],
    [0.86, 418],
    [0.94, 414],
    [1, 415],
  ]);
  const topY = sampleMotionStops(value, [
    [0.7, 465],
    [0.86, ORANGE_BODY_TOP_Y - 8],
    [0.94, ORANGE_BODY_TOP_Y + 4],
    [1, ORANGE_BODY_TOP_Y],
  ]);

  return buildOrangeShapePath(leftX, rightX, 244, topY, 550, 0);
}

function getOrangeEntranceFaceOpacity(progress: number) {
  return smoothstep((progress - 0.74) / 0.16);
}

const ORANGE_ENTRY_DURATION_MS = 1050;
const ORANGE_ENTRY_START_PATH = buildOrangeEntrancePath(0);
const ORANGE_FINAL_BODY_PATH = buildOrangeBodyPath(0, 0, 0);
const PURPLE_DEFAULT_BODY_PATH =
  "M212 550 C212 430 212 260 212 102 L404 102 C404 260 404 430 404 550 Z";

// Both animated and reduced-motion inputs use the same authored body endpoints.
function buildPurpleBodyPath(inputMix: number) {
  return inputMix > 0
    ? `M${svgPoint(212 - inputMix * 9)} 550V102H${svgPoint(404 + inputMix * 4)}V550Z`
    : PURPLE_DEFAULT_BODY_PATH;
}

// Authored gaze is independent of face position and ordinary pointer tracking.
function CharacterPoseEyes({
  character,
  pose,
}: {
  character: "purple" | "black";
  pose: "input" | "reveal";
}) {
  const clipId = useId();
  const purple = character === "purple";
  const input = pose === "input";
  const centers = purple ? [274, input ? 335 : 331] : [379, 411];
  const y = purple ? 145 : 278;
  const rx = purple ? 5.5 : 9;
  const ry = input ? rx : purple ? 6 : 10;

  return (
    <g className={`yak-login-character__${pose}-eyes`}>
      {centers.map((x, index) => (
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
              cx={x + (input ? 2.8 : purple ? 3 : 1.5)}
              cy={y - (input || purple ? 0 : 5)}
              r={input ? (purple ? 2.5 : 4.2) : purple ? 3.2 : 7}
              fill="#171717"
            />
          </g>
        </g>
      ))}
    </g>
  );
}

function PurpleCharacter() {
  return (
    <g data-character="purple" className="yak-login-character yak-login-character--purple">
      <g className="yak-login-character--purple__entry">
        <g className="yak-login-character--purple__breathe">
          <g className="yak-login-character--purple__result">
            <g className="yak-login-character--purple__focus">
              <g className="yak-login-character--purple__body">
                <path data-purple-body-path d={PURPLE_DEFAULT_BODY_PATH} fill="#6128F5" />
                <g className="yak-login-character--purple__face">
                  <g className="yak-login-character--purple__state-face-pose">
                    <g className="yak-login-character--purple__result-eyes">
                      <g className="yak-login-character--purple__focus-eyes">
                        <g className="yak-login-character--purple__eyes">
                          <circle cx="274" cy="145" r="5.5" fill="#FFFFFF" />
                          <circle
                            className="yak-login-character--purple__pupil"
                            cx="274"
                            cy="145"
                            r="2.3"
                            fill="#171717"
                          />
                          <circle cx="335" cy="145" r="5.5" fill="#FFFFFF" />
                          <circle
                            className="yak-login-character--purple__pupil"
                            cx="335"
                            cy="145"
                            r="2.3"
                            fill="#171717"
                          />
                        </g>
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
                    <path
                      className="yak-login-character__mouth yak-login-character__mouth--failure"
                      d="M292 181Q304.5 168 317 181"
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
  );
}

function BlackCharacter() {
  return (
    <g data-character="black" className="yak-login-character yak-login-character--black">
      <g className="yak-login-character--black__entry">
        <g className="yak-login-character--black__breathe">
          <g className="yak-login-character--black__result">
            <g className="yak-login-character--black__focus">
              <g className="yak-login-character--black__body">
                <path data-black-body-path d="M342 550V242H464V550Z" fill="#191A20" />
                <g className="yak-login-character--black__face">
                  <g className="yak-login-character--black__state-face-pose">
                    <g className="yak-login-character--black__result-eyes">
                      <g className="yak-login-character--black__focus-eyes">
                        <g className="yak-login-character--black__eyes">
                          <circle cx="379" cy="278" r="8.5" fill="#FFFFFF" />
                          <circle
                            className="yak-login-character--black__pupil"
                            cx="379"
                            cy="278"
                            r="3.4"
                            fill="#171717"
                          />
                          <circle cx="428" cy="278" r="8.5" fill="#FFFFFF" />
                          <circle
                            className="yak-login-character--black__pupil"
                            cx="428"
                            cy="278"
                            r="3.4"
                            fill="#171717"
                          />
                        </g>
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
                    <path
                      className="yak-login-character__mouth yak-login-character__mouth--failure"
                      d="M391 320Q403.5 307 416 320"
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
  );
}

function OrangeCharacter() {
  return (
    <g data-character="orange" className="yak-login-character yak-login-character--orange">
      <g className="yak-login-character--orange__entry">
        <g className="yak-login-character--orange__breathe">
          <g className="yak-login-character--orange__result">
            <g className="yak-login-character--orange__focus">
              <g className="yak-login-character--orange__body">
                <path data-orange-body-path d={ORANGE_ENTRY_START_PATH} fill="#FF7D2A" />
                <g className="yak-login-character--orange__face">
                  <g className="yak-login-character--orange__state-face-pose">
                    <g className="yak-login-character--orange__result-eyes">
                      <g className="yak-login-character--orange__focus-eyes">
                        <g className="yak-login-character--orange__eyes">
                          <g className="yak-login-character--orange__eye-pose">
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
                    </g>
                    <g className="yak-login-character--orange__mouth-pose">
                      <path
                        className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--happy"
                        d="M213 484Q213 482 215 482H246Q248 482 248 484C246.8 492.5 240 497 230.5 497C221 497 214.2 492.5 213 484Z"
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
                      <path
                        className="yak-login-character__mouth yak-login-character__mouth--failure"
                        d="M208 501Q230.5 480 253 501"
                        fill="none"
                        stroke="#171717"
                        strokeWidth="4"
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
  const [orangeBlinking, setOrangeBlinking] = useState(false);

  useLayoutEffect(() => {
    const scene = sceneRef.current;
    if (!scene || !window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const orangeBodyPath = scene.querySelector<SVGPathElement>("[data-orange-body-path]");
    if (!orangeBodyPath) return;

    orangeBodyPath.setAttribute("d", ORANGE_FINAL_BODY_PATH);
    scene.style.setProperty("--yak-orange-entry-face-opacity", "1");
  }, []);

  useLayoutEffect(() => {
    sceneStateRef.current = sceneState;
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const applyReducedScenePose = () => {
      if (media.matches) {
        const input = sceneState === "inputFocus";
        sceneRef.current
          ?.querySelector("[data-purple-body-path]")
          ?.setAttribute("d", buildPurpleBodyPath(input ? 1 : 0));
        sceneRef.current
          ?.querySelector("[data-black-body-path]")
          ?.setAttribute("d", buildBlackBodyPath(0, input ? 1 : 0));
        sceneRef.current
          ?.querySelector("[data-orange-body-path]")
          ?.setAttribute("d", buildOrangeBodyPath(0, 0, 0, input ? 1 : 0));
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

  useEffect(() => {
    if (sceneState !== "idle" || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      setOrangeBlinking(false);
      return;
    }

    let blinkEndTimer = 0;
    const blinkTimer = window.setInterval(() => {
      setOrangeBlinking(true);
      blinkEndTimer = window.setTimeout(() => {
        setOrangeBlinking(false);
      }, 180);
    }, 4600);

    return () => {
      window.clearInterval(blinkTimer);
      window.clearTimeout(blinkEndTimer);
    };
  }, [sceneState]);

  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let targetX = 0;
    let targetY = 0;
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
    let orangeEyeScale = 1;
    let orangeMouthRotate = 0;
    let yellowPointer: YellowPointer | null = null;
    let frame = 0;
    const orangeEntryStartedAt = performance.now();
    let previousFrameAt = orangeEntryStartedAt;
    let entranceInterrupted = false;

    const purpleBodyPath = scene.querySelector<SVGPathElement>("[data-purple-body-path]");
    const blackBodyPath = scene.querySelector<SVGPathElement>("[data-black-body-path]");
    const blackEntry = scene.querySelector<SVGGElement>(".yak-login-character--black__entry");
    // Use the actual CSS animation clock; do not duplicate its duration/delay in JavaScript.
    const blackEntryAnimation = blackEntry?.getAnimations()[0];
    const orangeBodyPath = scene.querySelector<SVGPathElement>("[data-orange-body-path]");
    if (!purpleBodyPath || !blackBodyPath || !orangeBodyPath) return;

    const clearYellowPointer = () => {
      yellowPointer = null;
    };
    const handlePointerOut = (event: PointerEvent) => {
      if (!event.relatedTarget) clearYellowPointer();
    };
    const handleVisibilityChange = () => {
      if (document.hidden) clearYellowPointer();
    };

    const handlePointerMove = (event: PointerEvent) => {
      yellowPointer = event.pointerType === "touch" ? null : { x: event.clientX, y: event.clientY };
      const rect = scene.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      targetX = clamp((event.clientX - (rect.left + rect.width / 2)) / (rect.width / 2), -1, 1);
      targetY = clamp((event.clientY - (rect.top + rect.height / 2)) / (rect.height / 2), -1, 1);
    };

    const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
    const animate = (now: number) => {
      if (motionPreference.matches) {
        previousFrameAt = now;
        inputMix = sceneStateRef.current === "inputFocus" ? 1 : 0;
        revealMix = sceneStateRef.current === "passwordVisible" ? 1 : 0;
        scene.style.setProperty("--yak-orange-entry-face-opacity", "1");
        frame = window.requestAnimationFrame(animate);
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
      purpleBodyPath.setAttribute("d", buildPurpleBodyPath(inputMix));
      if (
        activeSceneState === "inputFocus" ||
        activeSceneState === "passwordVisible" ||
        activeSceneState === "submitting" ||
        activeSceneState === "success" ||
        activeSceneState === "failure"
      ) {
        // Input and login feedback take priority; focus changes must not replay entrance.
        entranceInterrupted = true;
        scene.dataset.entranceInterrupted = "true";
      }
      const blackEntryProgress = entranceInterrupted
        ? 1
        : (blackEntryAnimation?.effect?.getComputedTiming().progress ?? 1);
      const blackBend = sampleMotionStops(blackEntryProgress, BLACK_ENTRY_BEND_STOPS);
      blackBodyPath.setAttribute("d", buildBlackBodyPath(blackBend, inputMix));
      const orangeEntryProgress = entranceInterrupted
        ? 1
        : clamp((now - orangeEntryStartedAt) / ORANGE_ENTRY_DURATION_MS, 0, 1);
      const orangeEntryComplete = orangeEntryProgress >= 1;
      const interactionTargetX = activeSceneState === "idle" ? targetX : 0;
      const interactionTargetY = activeSceneState === "idle" ? targetY : 0;
      purpleX = smoothMotion(purpleX, interactionTargetX, 0.075, deltaMs);
      purpleY = smoothMotion(purpleY, interactionTargetY, 0.075, deltaMs);
      blackX = smoothMotion(blackX, interactionTargetX, 0.042, deltaMs);
      blackY = smoothMotion(blackY, interactionTargetY, 0.042, deltaMs);

      const orangeTargetX = orangeEntryComplete ? interactionTargetX : 0;
      const orangeTargetY = orangeEntryComplete ? interactionTargetY : 0;

      const orangeSpringX = stepOrangeSpring(orangeX, orangeVelocityX, orangeTargetX, deltaMs);
      const orangeSpringY = stepOrangeSpring(orangeY, orangeVelocityY, orangeTargetY, deltaMs);
      orangeX = orangeSpringX.position;
      orangeY = orangeSpringY.position;
      orangeVelocityX = orangeSpringX.velocity;
      orangeVelocityY = orangeSpringY.velocity;
      orangeBodyX = smoothMotion(orangeBodyX, orangeTargetX, 0.045, deltaMs);
      orangeBodyY = smoothMotion(orangeBodyY, orangeTargetY, 0.04, deltaMs);

      const orangeActivity = clamp(
        Math.abs(orangeVelocityX) * 0.1 + Math.abs(orangeVelocityY) * (5 / 60),
        0,
        1,
      );

      scene.style.setProperty("--yak-purple-lean", `${purpleX * -8 * pointerWeight}deg`);
      scene.style.setProperty("--yak-purple-stretch", String(1 - purpleY * 0.04 * pointerWeight));
      scene.style.setProperty("--yak-purple-face-x", `${purpleX * 10 * pointerWeight}px`);
      scene.style.setProperty("--yak-purple-face-y", `${purpleY * 5 * pointerWeight}px`);
      scene.style.setProperty("--yak-purple-pupil-x", `${purpleX * 4 * pointerWeight}px`);
      scene.style.setProperty("--yak-purple-pupil-y", `${purpleY * 2.5 * pointerWeight}px`);

      scene.style.setProperty("--yak-black-lean", `${blackX * -4.5 * pointerWeight}deg`);
      scene.style.setProperty("--yak-black-stretch", String(1 - blackY * 0.018 * pointerWeight));
      scene.style.setProperty(
        "--yak-black-face-x",
        `${(blackX * 5 + blackBend * 0.81) * pointerWeight}px`,
      );
      scene.style.setProperty("--yak-black-face-y", `${blackY * 2.5 * pointerWeight}px`);
      scene.style.setProperty("--yak-black-face-rotate", `${blackBend * 0.09}deg`);
      scene.style.setProperty("--yak-black-pupil-x", `${blackX * 2.2 * pointerWeight}px`);
      scene.style.setProperty("--yak-black-pupil-y", `${blackY * 1.4 * pointerWeight}px`);

      if (orangeEntryComplete) {
        orangeBodyPath.setAttribute(
          "d",
          buildOrangeBodyPath(
            orangeBodyX * freePoseWeight,
            orangeBodyY * freePoseWeight,
            orangeActivity * freePoseWeight,
            inputMix,
          ),
        );
        scene.style.setProperty("--yak-orange-entry-face-opacity", "1");
      } else {
        orangeBodyPath.setAttribute("d", buildOrangeEntrancePath(orangeEntryProgress));
        scene.style.setProperty(
          "--yak-orange-entry-face-opacity",
          String(getOrangeEntranceFaceOpacity(orangeEntryProgress)),
        );
      }

      const orangePointerPoseEnabled = orangeEntryComplete && activeSceneState === "idle";
      const orangeFacePose = resolveOrangeFacePose(orangeX, orangeY, orangePointerPoseEnabled);

      scene.style.setProperty("--yak-orange-face-x", `${orangeFacePose.faceX * pointerWeight}px`);
      scene.style.setProperty("--yak-orange-face-y", `${orangeFacePose.faceY * pointerWeight}px`);
      orangeEyeScale = smoothMotion(orangeEyeScale, orangeFacePose.eyeScale, 0.18, deltaMs);
      orangeMouthRotate = smoothMotion(
        orangeMouthRotate,
        orangeFacePose.mouthRotate,
        0.16,
        deltaMs,
      );

      scene.style.setProperty("--yak-orange-eye-x", `${orangeFacePose.eyeX * pointerWeight}px`);
      scene.style.setProperty("--yak-orange-eye-y", `${orangeFacePose.eyeY * pointerWeight}px`);
      scene.style.setProperty(
        "--yak-orange-eye-scale",
        String(1 + (orangeEyeScale - 1) * pointerWeight),
      );
      scene.style.setProperty("--yak-orange-mouth-rotate", `${orangeMouthRotate * pointerWeight}deg`);

      yellowRef.current?.update(yellowPointer, activeSceneState, deltaMs, inputMix);

      frame = window.requestAnimationFrame(animate);
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("pointerout", handlePointerOut);
    window.addEventListener("blur", clearYellowPointer);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    frame = window.requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("pointerout", handlePointerOut);
      window.removeEventListener("blur", clearYellowPointer);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
      window.cancelAnimationFrame(frame);
      delete scene.dataset.entranceInterrupted;
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
  const orangeExpressionClass =
    sceneState === "idle" && orangeBlinking ? "is-orange-blink" : "is-orange-happy";

  return (
    <div
      ref={sceneRef}
      className={`yak-login-characters ${sceneClass} ${orangeExpressionClass} relative min-h-screen overflow-hidden bg-[#efedf2]`}
      data-scene-state={sceneState}
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
