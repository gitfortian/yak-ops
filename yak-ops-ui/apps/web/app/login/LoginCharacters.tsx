import { useEffect, useLayoutEffect, useRef, useState } from "react";

import "./login-characters.css";
import type { LoginFocusState, LoginResultState } from "./login-interaction";

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function svgPoint(value: number) {
  return Number(value.toFixed(2));
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
  const crownY = 392 - pointerLift - motionLift - sideLift;
  const leftLift = Math.max(-horizontal, 0) * 14 + Math.max(0, -vertical) * 7 + motion * 4;
  const rightLift = Math.max(horizontal, 0) * 14 + Math.max(0, -vertical) * 7 + motion * 4;

  return [
    "M65 550",
    `C65 458 ${svgPoint(142 + horizontal * 6)} ${svgPoint(392 - leftLift)}`,
    `${svgPoint(crownX)} ${svgPoint(crownY)}`,
    `C${svgPoint(346 + horizontal * 6)} ${svgPoint(392 - rightLift)} 415 458 415 550`,
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
  const topY = lerp(465, 392, phase) - Math.sin(phase * Math.PI) * 7;

  return buildOrangeShapePath(leftX, rightX, 244, topY, 550, 0);
}

function getOrangeEntranceFaceOpacity(progress: number) {
  return smoothstep((progress - 0.74) / 0.16);
}

const ORANGE_ENTRY_DURATION_MS = 1050;
const ORANGE_ENTRY_START_PATH = buildOrangeEntrancePath(0);
const ORANGE_FINAL_BODY_PATH = buildOrangeBodyPath(0, 0, 0);


function PurpleCharacter() {
  return (
    <g data-character="purple" className="yak-login-character yak-login-character--purple">
      <g className="yak-login-character--purple__entry">
        <g className="yak-login-character--purple__breathe">
          <g className="yak-login-character--purple__result">
            <g className="yak-login-character--purple__focus">
              <g className="yak-login-character--purple__body">
                <path d="M212 550V102H404V550Z" fill="#6128F5" />
                <g className="yak-login-character--purple__face">
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
                    className="yak-login-character__mouth yak-login-character__mouth--default"
                    cx="304.5"
                    cy="174"
                    rx="7.5"
                    ry="4.2"
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
                  <path
                    data-orange-body-path
                    d={ORANGE_ENTRY_START_PATH}
                    fill="#FF7D2A"
                  />
                  <g className="yak-login-character--orange__face">
                    <g className="yak-login-character--orange__result-eyes">
                      <g className="yak-login-character--orange__focus-eyes">
                        <g className="yak-login-character--orange__eyes">
                          <g className="yak-login-character--orange__eyes-open">
                            <circle cx="190" cy="462" r="6.9" fill="#171717" />
                            <circle cx="270" cy="462" r="6.9" fill="#171717" />
                          </g>
                          <g className="yak-login-character--orange__eyes-blink">
                            <path
                              d="M181 463Q190 454 199 463"
                              fill="none"
                              stroke="#171717"
                              strokeWidth="4"
                              strokeLinecap="round"
                            />
                            <path
                              d="M261 463Q270 454 279 463"
                              fill="none"
                              stroke="#171717"
                              strokeWidth="4"
                              strokeLinecap="round"
                            />
                          </g>
                        </g>
                      </g>
                    </g>
                    <path
                      className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--happy"
                      d="M213 491Q213 489 215 489H246Q248 489 248 491C246.8 501.5 240 508 230.5 508C221 508 214.2 501.5 213 491Z"
                      fill="#171717"
                    />
                    <circle
                      className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--input"
                      cx="231"
                      cy="496"
                      r="6"
                      fill="#171717"
                    />
                    <path
                      className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--password"
                      d="M214 494Q231 504 248 494"
                      fill="none"
                      stroke="#171717"
                      strokeWidth="4"
                      strokeLinecap="round"
                    />
                    <path
                      className="yak-login-character__mouth yak-login-character__mouth--success"
                      d="M208 487Q208 485 210.5 485H250.5Q253 485 253 487C251 504 242.5 513 230.5 513C218.5 513 210 504 208 487Z"
                      fill="#171717"
                    />
                    <path
                      className="yak-login-character__mouth yak-login-character__mouth--failure"
                      d="M208 507Q230.5 486 253 507"
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
  const focusStateRef = useRef(focusState);
  const resultStateRef = useRef(resultState);
  const passwordVisibleRef = useRef(passwordVisible);
  const [orangeBlinking, setOrangeBlinking] = useState(false);

  useLayoutEffect(() => {
    const scene = sceneRef.current;
    if (!scene || !window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    const orangeBodyPath = scene.querySelector<SVGPathElement>("[data-orange-body-path]");
    if (!orangeBodyPath) return;

    orangeBodyPath.setAttribute("d", ORANGE_FINAL_BODY_PATH);
    scene.style.setProperty("--yak-orange-entry-face-opacity", "1");
  }, []);

  useEffect(() => {
    focusStateRef.current = focusState;
  }, [focusState]);

  useEffect(() => {
    resultStateRef.current = resultState;
  }, [resultState]);

  useEffect(() => {
    passwordVisibleRef.current = passwordVisible;
  }, [passwordVisible]);

  useEffect(() => {
    if (focusState !== "idle" || resultState !== "idle") {
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
  }, [focusState, resultState]);

  useEffect(() => {
    const scene = sceneRef.current;
    if (!scene || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

    let targetX = 0;
    let targetY = 0;
    let purpleX = 0;
    let purpleY = 0;
    let blackX = 0;
    let blackY = 0;
    let orangeX = 0;
    let orangeY = 0;
    let orangeVelocityX = 0;
    let orangeVelocityY = 0;
    let orangeBodyX = 0;
    let orangeBodyY = 0;
    let yellowBodyX = 0;
    let yellowBodyY = 0;
    let yellowFaceX = 0;
    let yellowFaceY = 0;
    let frame = 0;
    const orangeEntryStartedAt = performance.now();

    const orangeBodyPath = scene.querySelector<SVGPathElement>("[data-orange-body-path]");
    const yellowBodyPath = scene.querySelector<SVGPathElement>("[data-yellow-body-path]");
    if (!orangeBodyPath || !yellowBodyPath) return;

    const handlePointerMove = (event: PointerEvent) => {
      const rect = scene.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      targetX = clamp((event.clientX - (rect.left + rect.width / 2)) / (rect.width / 2), -1, 1);
      targetY = clamp((event.clientY - (rect.top + rect.height / 2)) / (rect.height / 2), -1, 1);
    };

    const animate = () => {
      const orangeEntryProgress = clamp(
        (performance.now() - orangeEntryStartedAt) / ORANGE_ENTRY_DURATION_MS,
        0,
        1,
      );
      const orangeEntryComplete = orangeEntryProgress >= 1;
      const activeFocus = focusStateRef.current;
      const activeResult = resultStateRef.current;
      const passwordPeek = activeFocus === "userPassword" && passwordVisibleRef.current;
      const interactionTargetX =
        activeResult !== "idle"
          ? 0
          : activeFocus === "userName"
            ? 1
            : passwordPeek
              ? 1.12
              : activeFocus === "userPassword"
                ? -0.82
                : targetX;
      const interactionTargetY =
        activeResult !== "idle"
          ? 0
          : activeFocus === "userName"
            ? 0.08
            : passwordPeek
              ? 0.02
              : activeFocus === "userPassword"
                ? 0.28
                : targetY;

      purpleX += (interactionTargetX - purpleX) * 0.075;
      purpleY += (interactionTargetY - purpleY) * 0.075;

      blackX += (interactionTargetX - blackX) * 0.042;
      blackY += (interactionTargetY - blackY) * 0.042;

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

      scene.style.setProperty("--yak-purple-lean", `${purpleX * -8}deg`);
      scene.style.setProperty("--yak-purple-stretch", String(1 - purpleY * 0.04));
      scene.style.setProperty("--yak-purple-face-x", `${purpleX * 10}px`);
      scene.style.setProperty("--yak-purple-face-y", `${purpleY * 5}px`);
      scene.style.setProperty("--yak-purple-pupil-x", `${purpleX * 4}px`);
      scene.style.setProperty("--yak-purple-pupil-y", `${purpleY * 2.5}px`);

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

      scene.style.setProperty("--yak-orange-face-x", `${orangeX * 34}px`);
      scene.style.setProperty("--yak-orange-face-y", `${orangeY * 14}px`);
      scene.style.setProperty("--yak-orange-eye-x", `${orangeX * 6}px`);
      scene.style.setProperty("--yak-orange-eye-y", `${orangeY * 3}px`);

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

  const focusClass =
    resultState === "idle"
      ? focusState === "userName"
        ? "is-user-focus"
        : focusState === "userPassword"
          ? "is-password-focus"
          : ""
      : "";
  const visibilityClass =
    resultState === "idle" && focusState === "userPassword" && passwordVisible
      ? "is-password-visible"
      : "";
  const resultClass =
    resultState === "success"
      ? "is-login-success"
      : resultState === "failure"
        ? "is-login-failure"
        : "";
  const orangeExpressionClass =
    focusState === "idle" && resultState === "idle" && orangeBlinking
      ? "is-orange-blink"
      : "is-orange-happy";

  return (
    <div
      ref={sceneRef}
      className={`yak-login-characters ${focusClass} ${visibilityClass} ${resultClass} ${orangeExpressionClass} flex min-h-screen items-end justify-center overflow-hidden bg-[#efedf2]`}
      aria-hidden="true"
    >
      <svg
        className="yak-login-characters__svg h-auto w-[min(88%,820px)]"
        viewBox="0 0 720 580"
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
