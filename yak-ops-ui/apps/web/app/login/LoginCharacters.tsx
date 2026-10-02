import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";

import YellowCharacter, { type YellowCharacterHandle, type YellowPointer } from "./YellowCharacter";
import "./login-characters.css";
import {
  getPurplePasswordBow,
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
function buildBlackBodyPath(bend: number) {
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

function resolvePurpleBowGeometry(direction: number, bow: number) {
  const normalizedDirection = clamp(direction, -1, 1);
  const normalizedBow = clamp(bow, 0, 1);

  const topShift = normalizedDirection * 140 * normalizedBow;
  const middleShift = topShift * 0.55;
  const lowerShift = topShift * 0.12;
  const topTiltY = normalizedDirection * 36 * normalizedBow;

  const leftTopX = 212 + topShift;
  const rightTopX = 404 + topShift;
  const leftTopY = 102 - topTiltY * 0.5;
  const rightTopY = 102 + topTiltY * 0.5;

  const bodyPath = [
    "M212 550",
    `C${svgPoint(212 + lowerShift * 0.2)} 430`,
    `${svgPoint(212 + middleShift * 0.45)} 260`,
    `${svgPoint(leftTopX)} ${svgPoint(leftTopY)}`,
    `L${svgPoint(rightTopX)} ${svgPoint(rightTopY)}`,
    `C${svgPoint(404 + middleShift * 0.95)} 260`,
    `${svgPoint(404 + lowerShift * 0.3)} 430`,
    "404 550",
    "Z",
  ].join(" ");

  const faceRotate = (Math.atan2(rightTopY - leftTopY, rightTopX - leftTopX) * 180) / Math.PI;

  return {
    bodyPath,
    faceX: topShift,
    faceY: (leftTopY + rightTopY) / 2 - 102,
    faceRotate,
  };
}

function buildOrangeBodyPath(x: number, y: number, activity: number) {
  const horizontal = clamp(x, -1, 1);
  const vertical = clamp(y, -1, 1);
  const motion = clamp(activity, 0, 1);

  const pointerLift = Math.max(0, -vertical) * 14;
  const motionLift = motion * 8;
  const sideLift = Math.abs(horizontal) * 5;
  const crownX = 244 + horizontal * 18;
  const crownY = ORANGE_BODY_TOP_Y - pointerLift - motionLift - sideLift;
  const sideY = svgPoint(ORANGE_BODY_TOP_Y + (550 - ORANGE_BODY_TOP_Y) * 0.42);
  const leftLift = Math.max(-horizontal, 0) * 14 + Math.max(0, -vertical) * 7 + motion * 4;
  const rightLift = Math.max(horizontal, 0) * 14 + Math.max(0, -vertical) * 7 + motion * 4;

  return [
    "M65 550",
    `C65 ${sideY} ${svgPoint(142 + horizontal * 6)} ${svgPoint(ORANGE_BODY_TOP_Y - leftLift)}`,
    `${svgPoint(crownX)} ${svgPoint(crownY)}`,
    `C${svgPoint(346.05 + horizontal * 6)} ${svgPoint(ORANGE_BODY_TOP_Y - rightLift)} 415 ${sideY} 415 550`,
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
const PURPLE_DEFAULT_BODY_PATH = resolvePurpleBowGeometry(0, 0).bodyPath;

// Reveal gaze is authored independently from face position and ordinary pointer tracking.
function PasswordRevealEyes({ character }: { character: "purple" | "black" }) {
  const clipId = useId();
  const purple = character === "purple";
  const centers = purple ? [274, 331] : [379, 411];
  const y = purple ? 145 : 278;
  const rx = purple ? 5.5 : 9;
  const ry = purple ? 6 : 10;

  return (
    <g className="yak-login-character__reveal-eyes">
      {centers.map((x, index) => (
        <g key={x}>
          <defs>
            <clipPath id={`${clipId}-${index}`} clipPathUnits="userSpaceOnUse">
              <ellipse cx={x} cy={y} rx={rx} ry={ry} />
            </clipPath>
          </defs>
          <ellipse data-reveal-eye cx={x} cy={y} rx={rx} ry={ry} fill="#FFFFFF" />
          <g clipPath={`url(#${clipId}-${index})`}>
            <circle
              data-reveal-pupil
              cx={x + (purple ? 3 : 1.5)}
              cy={y - (purple ? 0 : 5)}
              r={purple ? 3.2 : 7}
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
                  <g className="yak-login-character--purple__reveal-face-pose">
                    <g className="yak-login-character--purple__password-face-pose">
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
                          <PasswordRevealEyes character="purple" />
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
                        cy="176"
                        rx="5.5"
                        ry="11"
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
                  <g className="yak-login-character--black__reveal-face-pose">
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
                        <PasswordRevealEyes character="black" />
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
                  <g className="yak-login-character--orange__reveal-face-pose">
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
                        cy="490"
                        r="6"
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
    let purplePasswordBow = 0;
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
    let previousSceneState = sceneStateRef.current;
    let sceneStateStartedAt = orangeEntryStartedAt;
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

    const animate = (now: number) => {
      const elapsedMs = Math.max(0, now - previousFrameAt);
      previousFrameAt = now;
      // A suspended tab must not integrate seconds of old spring velocity on resume.
      const deltaMs = elapsedMs > 250 ? 0 : Math.min(elapsedMs, 64);
      if (elapsedMs > 250) {
        orangeVelocityX = 0;
        orangeVelocityY = 0;
      }
      const activeSceneState = sceneStateRef.current;
      if (
        activeSceneState === "passwordVisible" ||
        activeSceneState === "submitting" ||
        activeSceneState === "success" ||
        activeSceneState === "failure"
      ) {
        // Reveal and login feedback take priority; hiding/retrying must not replay entrance.
        entranceInterrupted = true;
        scene.dataset.entranceInterrupted = "true";
      }
      const blackEntryProgress = entranceInterrupted
        ? 1
        : (blackEntryAnimation?.effect?.getComputedTiming().progress ?? 1);
      const blackBend = sampleMotionStops(blackEntryProgress, BLACK_ENTRY_BEND_STOPS);
      blackBodyPath.setAttribute("d", buildBlackBodyPath(blackBend));
      const orangeEntryProgress = entranceInterrupted
        ? 1
        : clamp((now - orangeEntryStartedAt) / ORANGE_ENTRY_DURATION_MS, 0, 1);
      const orangeEntryComplete = orangeEntryProgress >= 1;
      if (activeSceneState !== previousSceneState) {
        previousSceneState = activeSceneState;
        sceneStateStartedAt = now;
      }

      const passwordHidden = activeSceneState === "passwordHidden";
      const passwordShown = activeSceneState === "passwordVisible";
      const inputFocused = activeSceneState === "userName" || passwordHidden;
      const interactionTargetX = activeSceneState === "idle" ? targetX : inputFocused ? 1 : 0;
      const interactionTargetY = activeSceneState === "idle" ? targetY : inputFocused ? 0.08 : 0;

      // The bow is an interruptible entry gesture, not the held password pose.
      // No delayed callback can restore a stale focus/visibility state.
      const purplePasswordBowTarget = passwordHidden
        ? getPurplePasswordBow(now - sceneStateStartedAt)
        : 0;
      purpleX = smoothMotion(purpleX, interactionTargetX, 0.075, deltaMs);
      purpleY = smoothMotion(purpleY, interactionTargetY, 0.075, deltaMs);
      purplePasswordBow = smoothMotion(purplePasswordBow, purplePasswordBowTarget, 0.1, deltaMs);

      blackX = smoothMotion(blackX, interactionTargetX, 0.042, deltaMs);
      blackY = smoothMotion(blackY, interactionTargetY, 0.042, deltaMs);

      const revealTarget = passwordShown ? 1 : 0;
      revealMix = smoothMotion(revealMix, revealTarget, 0.2, deltaMs);
      if (Math.abs(revealMix - revealTarget) < 0.001) revealMix = revealTarget;
      const freePoseWeight = 1 - revealMix;

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

      const purpleBowGeometry = resolvePurpleBowGeometry(0.82, purplePasswordBow * freePoseWeight);
      const purpleHeldPoseWeight = 1 - purplePasswordBow;
      const purplePointerLean = purpleX * -8 * purpleHeldPoseWeight;
      const purplePointerStretch = 1 - purpleY * 0.04 * purpleHeldPoseWeight;
      const purplePointerFaceX = purpleX * 10 * purpleHeldPoseWeight;
      const purplePointerFaceY = purpleY * 5 * purpleHeldPoseWeight;
      const purplePointerPupilX = purpleX * 4 * purpleHeldPoseWeight;
      const purplePointerPupilY = purpleY * 2.5 * purpleHeldPoseWeight;

      purpleBodyPath.setAttribute("d", purpleBowGeometry.bodyPath);
      scene.style.setProperty("--yak-purple-lean", `${purplePointerLean}deg`);
      scene.style.setProperty("--yak-purple-stretch", String(purplePointerStretch));
      scene.style.setProperty("--yak-purple-face-x", `${purplePointerFaceX}px`);
      scene.style.setProperty("--yak-purple-face-y", `${purplePointerFaceY}px`);
      scene.style.setProperty("--yak-purple-pupil-x", `${purplePointerPupilX}px`);
      scene.style.setProperty("--yak-purple-pupil-y", `${purplePointerPupilY}px`);
      scene.style.setProperty("--yak-purple-password-face-x", `${purpleBowGeometry.faceX}px`);
      scene.style.setProperty("--yak-purple-password-face-y", `${purpleBowGeometry.faceY}px`);
      scene.style.setProperty(
        "--yak-purple-password-face-rotate",
        `${purpleBowGeometry.faceRotate}deg`,
      );

      scene.style.setProperty("--yak-black-lean", `${blackX * -4.5}deg`);
      scene.style.setProperty("--yak-black-stretch", String(1 - blackY * 0.018));
      scene.style.setProperty("--yak-black-face-x", `${blackX * 5 + blackBend * 0.81}px`);
      scene.style.setProperty("--yak-black-face-y", `${blackY * 2.5}px`);
      scene.style.setProperty("--yak-black-face-rotate", `${blackBend * 0.09}deg`);
      scene.style.setProperty("--yak-black-pupil-x", `${blackX * 2.2}px`);
      scene.style.setProperty("--yak-black-pupil-y", `${blackY * 1.4}px`);

      if (orangeEntryComplete) {
        orangeBodyPath.setAttribute(
          "d",
          buildOrangeBodyPath(
            orangeBodyX * freePoseWeight,
            orangeBodyY * freePoseWeight,
            orangeActivity * freePoseWeight,
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

      scene.style.setProperty("--yak-orange-face-x", `${orangeFacePose.faceX}px`);
      scene.style.setProperty("--yak-orange-face-y", `${orangeFacePose.faceY}px`);
      orangeEyeScale = smoothMotion(orangeEyeScale, orangeFacePose.eyeScale, 0.18, deltaMs);
      orangeMouthRotate = smoothMotion(
        orangeMouthRotate,
        orangeFacePose.mouthRotate,
        0.16,
        deltaMs,
      );

      scene.style.setProperty("--yak-orange-eye-x", `${orangeFacePose.eyeX}px`);
      scene.style.setProperty("--yak-orange-eye-y", `${orangeFacePose.eyeY}px`);
      scene.style.setProperty("--yak-orange-eye-scale", String(orangeEyeScale));
      scene.style.setProperty("--yak-orange-mouth-rotate", `${orangeMouthRotate}deg`);

      yellowRef.current?.update(yellowPointer, activeSceneState, deltaMs);

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
    userName: "is-user-focus is-input-focus",
    passwordHidden: "is-password-focus is-input-focus",
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
