import { useEffect, useLayoutEffect, useRef, useState } from "react";

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

function buildYellowBodyPath(x: number, y: number) {
  const bend = clamp(x, -1, 1) * 30;
  const vertical = clamp(y, -1, 1);

  const topY = 318 + vertical * 10;
  const shoulderY = 352 + vertical * 5;
  const rightShoulderY = 349 + vertical * 6;
  const rightSideY = 404 + vertical * 3;

  return [
    "M450 550",
    "V407",
    `C${svgPoint(450 + bend * 0.08)} ${svgPoint(shoulderY)}`,
    `${svgPoint(481 + bend * 0.55)} ${svgPoint(topY)}`,
    `${svgPoint(524 + bend)} ${svgPoint(topY)}`,
    `C${svgPoint(565 + bend)} ${svgPoint(topY)}`,
    `${svgPoint(590 + bend * 0.65)} ${svgPoint(rightShoulderY)}`,
    `${svgPoint(590 + bend * 0.3)} ${svgPoint(rightSideY)}`,
    `C${svgPoint(590 + bend * 0.12)} 456 590 505 590 550`,
    "Z",
  ].join(" ");
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
    const phase = smoothstep(value / 0.42);
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

  const phase = smoothstep((value - 0.7) / 0.3);
  const leftX = lerp(134, 65, phase);
  const rightX = lerp(354, 415, phase);
  const topY = lerp(465, ORANGE_BODY_TOP_Y, phase) - Math.sin(phase * Math.PI) * 7;

  return buildOrangeShapePath(leftX, rightX, 244, topY, 550, 0);
}

function getOrangeEntranceFaceOpacity(progress: number) {
  return smoothstep((progress - 0.74) / 0.16);
}

const ORANGE_ENTRY_DURATION_MS = 1050;
const ORANGE_ENTRY_START_PATH = buildOrangeEntrancePath(0);
const ORANGE_FINAL_BODY_PATH = buildOrangeBodyPath(0, 0, 0);
const PURPLE_DEFAULT_BODY_PATH = resolvePurpleBowGeometry(0, 0).bodyPath;

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
                <path d="M342 550V242H464V550Z" fill="#191A20" />
                <g className="yak-login-character--black__face">
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
  );
}

function YellowCharacter() {
  return (
    <g data-character="yellow" className="yak-login-character yak-login-character--yellow">
      <g className="yak-login-character--yellow__entry">
        <g className="yak-login-character--yellow__breathe">
          <g className="yak-login-character--yellow__result">
            <g className="yak-login-character--yellow__focus">
              <g className="yak-login-character--yellow__body">
                <path
                  data-yellow-body-path
                  d="M450 550V407C450 352 481 318 524 318C565 318 590 349 590 404C590 456 590 505 590 550Z"
                  fill="#F3D30B"
                />
                <g className="yak-login-character--yellow__face">
                  <g className="yak-login-character--yellow__result-eyes">
                    <g className="yak-login-character--yellow__focus-eyes">
                      <g className="yak-login-character--yellow__eyes">
                        <circle cx="532" cy="376" r="5.4" fill="#171717" />
                      </g>
                    </g>
                  </g>
                  <path
                    className="yak-login-character__mouth yak-login-character__mouth--default"
                    d="M563 411H625"
                    fill="none"
                    stroke="#171717"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                  <path
                    className="yak-login-character__mouth yak-login-character__mouth--success"
                    d="M562 406Q592 424 625 408"
                    fill="none"
                    stroke="#171717"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                  <path
                    className="yak-login-character__mouth yak-login-character__mouth--failure"
                    d="M562 417Q592 401 625 416"
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
                      d="M214 488Q231 498 248 488"
                      fill="none"
                      stroke="#171717"
                      strokeWidth="4"
                      strokeLinecap="round"
                    />
                    <path
                      className="yak-login-character__mouth yak-login-character__mouth--success"
                      d="M208 481Q208 479 210.5 479H250.5Q253 479253 481C251 495 242.5 504 230.5 504C218.5 504 210 495 208 481Z"
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
    let yellowBodyX = 0;
    let yellowBodyY = 0;
    let yellowFaceX = 0;
    let yellowFaceY = 0;
    let frame = 0;
    const orangeEntryStartedAt = performance.now();
    let previousSceneState = sceneStateRef.current;
    let sceneStateStartedAt = orangeEntryStartedAt;

    const purpleBodyPath = scene.querySelector<SVGPathElement>("[data-purple-body-path]");
    const orangeBodyPath = scene.querySelector<SVGPathElement>("[data-orange-body-path]");
    const yellowBodyPath = scene.querySelector<SVGPathElement>("[data-yellow-body-path]");
    if (!purpleBodyPath || !orangeBodyPath || !yellowBodyPath) return;

    const handlePointerMove = (event: PointerEvent) => {
      const rect = scene.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      targetX = clamp((event.clientX - (rect.left + rect.width / 2)) / (rect.width / 2), -1, 1);
      targetY = clamp((event.clientY - (rect.top + rect.height / 2)) / (rect.height / 2), -1, 1);
    };

    const animate = (now: number) => {
      const orangeEntryProgress = clamp(
        (now - orangeEntryStartedAt) / ORANGE_ENTRY_DURATION_MS,
        0,
        1,
      );
      const orangeEntryComplete = orangeEntryProgress >= 1;
      const activeSceneState = sceneStateRef.current;
      if (activeSceneState !== previousSceneState) {
        previousSceneState = activeSceneState;
        sceneStateStartedAt = now;
      }

      const passwordHidden = activeSceneState === "passwordHidden";
      const passwordShown = activeSceneState === "passwordVisible";
      const inputFocused = activeSceneState === "userName" || passwordHidden;
      const interactionTargetX =
        activeSceneState === "idle"
          ? targetX
          : inputFocused
            ? 1
            : passwordShown
              ? -0.82
              : 0;
      const interactionTargetY =
        activeSceneState === "idle"
          ? targetY
          : inputFocused
            ? 0.08
            : passwordShown
              ? 0.28
              : 0;

      // The bow is an interruptible entry gesture, not the held password pose.
      // No delayed callback can restore a stale focus/visibility state.
      const purplePasswordBowTarget = passwordHidden
        ? getPurplePasswordBow(now - sceneStateStartedAt)
        : 0;
      purpleX += (interactionTargetX - purpleX) * 0.075;
      purpleY += (interactionTargetY - purpleY) * 0.075;
      purplePasswordBow += (purplePasswordBowTarget - purplePasswordBow) * 0.1;

      // Black observes while orange/purple look away; do not give every role the same pose.
      const blackTargetX = passwordShown ? 0.35 : interactionTargetX;
      const blackTargetY = passwordShown ? 0.12 : interactionTargetY;
      blackX += (blackTargetX - blackX) * 0.042;
      blackY += (blackTargetY - blackY) * 0.042;

      const orangeTargetX = orangeEntryComplete ? interactionTargetX : 0;
      const orangeTargetY = orangeEntryComplete ? interactionTargetY : 0;

      orangeVelocityX += (orangeTargetX - orangeX) * 0.014;
      orangeVelocityY += (orangeTargetY - orangeY) * 0.014;
      orangeVelocityX *= 0.76;
      orangeVelocityY *= 0.76;
      orangeX += orangeVelocityX;
      orangeY += orangeVelocityY;
      orangeBodyX += (orangeTargetX - orangeBodyX) * 0.045;
      orangeBodyY += (orangeTargetY - orangeBodyY) * 0.04;

      yellowBodyX += (interactionTargetX - yellowBodyX) * 0.055;
      yellowBodyY += (interactionTargetY - yellowBodyY) * 0.05;
      yellowFaceX += (interactionTargetX - yellowFaceX) * 0.095;
      yellowFaceY += (interactionTargetY - yellowFaceY) * 0.085;

      const orangeActivity = clamp(
        Math.abs(orangeVelocityX) * 6 + Math.abs(orangeVelocityY) * 5,
        0,
        1,
      );

      const purpleBowGeometry = resolvePurpleBowGeometry(0.82, purplePasswordBow);
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
      scene.style.setProperty("--yak-black-face-x", `${blackX * 5}px`);
      scene.style.setProperty("--yak-black-face-y", `${blackY * 2.5}px`);
      scene.style.setProperty("--yak-black-pupil-x", `${blackX * 2.2}px`);
      scene.style.setProperty("--yak-black-pupil-y", `${blackY * 1.4}px`);

      if (orangeEntryComplete) {
        orangeBodyPath.setAttribute(
          "d",
          buildOrangeBodyPath(orangeBodyX, orangeBodyY, orangeActivity),
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
      orangeEyeScale += (orangeFacePose.eyeScale - orangeEyeScale) * 0.18;
      orangeMouthRotate += (orangeFacePose.mouthRotate - orangeMouthRotate) * 0.16;

      scene.style.setProperty("--yak-orange-eye-x", `${orangeFacePose.eyeX}px`);
      scene.style.setProperty("--yak-orange-eye-y", `${orangeFacePose.eyeY}px`);
      scene.style.setProperty("--yak-orange-eye-scale", String(orangeEyeScale));
      scene.style.setProperty("--yak-orange-mouth-rotate", `${orangeMouthRotate}deg`);

      yellowBodyPath.setAttribute("d", buildYellowBodyPath(yellowBodyX, yellowBodyY));
      scene.style.setProperty("--yak-yellow-face-x", `${yellowFaceX * 10}px`);
      scene.style.setProperty("--yak-yellow-face-y", `${yellowFaceY * 4}px`);
      scene.style.setProperty("--yak-yellow-eye-x", `${yellowFaceX * 4}px`);
      scene.style.setProperty("--yak-yellow-eye-y", `${yellowFaceY * 2}px`);

      frame = window.requestAnimationFrame(animate);
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    frame = window.requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("pointermove", handlePointerMove);
      window.cancelAnimationFrame(frame);
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
        <YellowCharacter />
        <OrangeCharacter />
      </svg>
    </div>
  );
}
