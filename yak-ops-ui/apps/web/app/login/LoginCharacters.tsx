import { useEffect, useRef, useState } from "react";

import "./login-characters.css";
import type { LoginFocusState, LoginResultState } from "./login-interaction";

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

type OrangeExpression = "happy" | "curious" | "worried" | "blink";
type OrangeBaseExpression = Exclude<OrangeExpression, "blink">;

function resolveOrangeExpression(
  x: number,
  y: number,
  current: OrangeBaseExpression,
): OrangeBaseExpression {
  if (current === "worried" && y > 0.32) return "worried";
  if (current === "curious" && Math.abs(x) > 0.58 && y < 0.28) return "curious";
  if (y > 0.48) return "worried";
  if (Math.abs(x) > 0.72 && y < 0.18) return "curious";
  return "happy";
}

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
                  d="M450 550V407C450 352 481 318 524 318C565 318 590 349 590 404V550Z"
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
                <path d="M65 550C65 458 142 392 244 392C346 392 415 458 415 550Z" fill="#FF7D2A" />
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
                    d="M210 489H251C249 507 241 516 230.5 516C220 516 212 507 210 489Z"
                    fill="#171717"
                  />
                  <ellipse
                    className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--curious"
                    cx="231"
                    cy="495"
                    rx="5.8"
                    ry="4.8"
                    fill="#171717"
                  />
                  <path
                    className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--worried"
                    d="M211 505Q231 486 251 505"
                    fill="none"
                    stroke="#171717"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                  <path
                    className="yak-login-character__mouth yak-login-character__mouth--default yak-login-character--orange__mouth yak-login-character--orange__mouth--blink"
                    d="M214 493Q231 504 248 493"
                    fill="none"
                    stroke="#171717"
                    strokeWidth="4"
                    strokeLinecap="round"
                  />
                  <path
                    className="yak-login-character__mouth yak-login-character__mouth--success"
                    d="M204 485H257C254 509 244 521 230.5 521C217 521 207 509 204 485Z"
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
  const orangeBaseExpressionRef = useRef<OrangeBaseExpression>("happy");
  const orangeBlinkingRef = useRef(false);
  const [orangeExpression, setOrangeExpression] = useState<OrangeExpression>("happy");

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
      orangeBlinkingRef.current = false;
      orangeBaseExpressionRef.current = "happy";
      setOrangeExpression("happy");
      return;
    }

    let blinkEndTimer = 0;
    const blinkTimer = window.setInterval(() => {
      orangeBlinkingRef.current = true;
      setOrangeExpression("blink");
      blinkEndTimer = window.setTimeout(() => {
        orangeBlinkingRef.current = false;
        setOrangeExpression(orangeBaseExpressionRef.current);
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
    let yellowX = 0;
    let yellowY = 0;
    let yellowVelocityX = 0;
    let yellowVelocityY = 0;
    let frame = 0;

    const handlePointerMove = (event: PointerEvent) => {
      const rect = scene.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      targetX = clamp((event.clientX - (rect.left + rect.width / 2)) / (rect.width / 2), -1, 1);
      targetY = clamp((event.clientY - (rect.top + rect.height / 2)) / (rect.height / 2), -1, 1);
    };

    const animate = () => {
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

      orangeVelocityX += (interactionTargetX - orangeX) * 0.014;
      orangeVelocityY += (interactionTargetY - orangeY) * 0.014;
      orangeVelocityX *= 0.76;
      orangeVelocityY *= 0.76;
      orangeX += orangeVelocityX;
      orangeY += orangeVelocityY;

      yellowVelocityX += (interactionTargetX - yellowX) * 0.028;
      yellowVelocityY += (interactionTargetY - yellowY) * 0.028;
      yellowVelocityX *= 0.76;
      yellowVelocityY *= 0.76;
      yellowX += yellowVelocityX;
      yellowY += yellowVelocityY;

      const orangeSquash = clamp(
        Math.abs(orangeVelocityX) * 0.35 + Math.abs(orangeVelocityY) * 0.22,
        0,
        0.018,
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

      scene.style.setProperty("--yak-orange-bend", `${orangeX * -1.8}deg`);
      scene.style.setProperty(
        "--yak-orange-scale-y",
        String(1 - orangeY * 0.012 - orangeSquash * 0.35),
      );
      scene.style.setProperty("--yak-orange-face-x", `${orangeX * 34}px`);
      scene.style.setProperty("--yak-orange-face-y", `${orangeY * 14}px`);
      scene.style.setProperty("--yak-orange-eye-x", `${orangeX * 6}px`);
      scene.style.setProperty("--yak-orange-eye-y", `${orangeY * 3}px`);

      if (activeFocus === "idle" && activeResult === "idle" && !orangeBlinkingRef.current) {
        const nextExpression = resolveOrangeExpression(
          orangeX,
          orangeY,
          orangeBaseExpressionRef.current,
        );
        if (nextExpression !== orangeBaseExpressionRef.current) {
          orangeBaseExpressionRef.current = nextExpression;
          setOrangeExpression(nextExpression);
        }
      }

      scene.style.setProperty("--yak-yellow-bend", `${yellowX * -6.5}deg`);
      scene.style.setProperty("--yak-yellow-stretch", String(1 - yellowY * 0.026));
      scene.style.setProperty("--yak-yellow-face-x", `${yellowX * 8}px`);
      scene.style.setProperty("--yak-yellow-face-y", `${yellowY * 3.5}px`);
      scene.style.setProperty("--yak-yellow-eye-x", `${yellowX * 3.6}px`);
      scene.style.setProperty("--yak-yellow-eye-y", `${yellowY * 1.8}px`);

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
    focusState === "idle" && resultState === "idle"
      ? `is-orange-${orangeExpression}`
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
