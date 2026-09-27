import { useEffect, useRef } from "react";

export type ActionType = "BLINK" | "SMILE" | "SURPRISE" | "SHAKE" | "THANKS";
export type FocusedField = "userName" | "userPassword" | null;

export interface LoginSceneAction {
  type: ActionType;
  nonce: number;
}

interface LoginSceneProps {
  action: LoginSceneAction;
  focusedField: FocusedField;
}

const ACTION_CLASS: Record<ActionType, string> = {
  BLINK: "is-blink",
  SMILE: "is-smile",
  SURPRISE: "is-surprise",
  SHAKE: "is-shake",
  THANKS: "is-thanks",
};

const ACTION_DURATION: Record<ActionType, number> = {
  BLINK: 280,
  SMILE: 900,
  SURPRISE: 720,
  SHAKE: 720,
  THANKS: 760,
};

const ACTION_CLASSES = Object.values(ACTION_CLASS);

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function Face({
  eyeColor,
  pupilColor,
  leftEyeX,
  rightEyeX,
  eyeY,
  eyeRadius,
  pupilRadius,
  mouthY,
  mouthWidth,
  neutralMouth = "smile",
  whiteEyes = false,
}: {
  eyeColor: string;
  pupilColor: string;
  leftEyeX: number;
  rightEyeX: number;
  eyeY: number;
  eyeRadius: number;
  pupilRadius: number;
  mouthY: number;
  mouthWidth: number;
  neutralMouth?: "smile" | "line" | "none";
  whiteEyes?: boolean;
}) {
  const centerX = (leftEyeX + rightEyeX) / 2;
  const neutralPath =
    neutralMouth === "line"
      ? `M ${centerX - mouthWidth / 2} ${mouthY} H ${centerX + mouthWidth / 2}`
      : `M ${centerX - mouthWidth / 2} ${mouthY} Q ${centerX} ${mouthY + 5} ${centerX + mouthWidth / 2} ${mouthY}`;

  return (
    <g className="yak-mascot__face">
      <g className="yak-mascot__eyes">
        {whiteEyes ? (
          <>
            <g className="yak-mascot__eye">
              <circle cx={leftEyeX} cy={eyeY} r={eyeRadius} fill={eyeColor} />
              <circle
                className="yak-mascot__pupil"
                cx={leftEyeX}
                cy={eyeY}
                r={pupilRadius}
                fill={pupilColor}
              />
            </g>
            <g className="yak-mascot__eye">
              <circle cx={rightEyeX} cy={eyeY} r={eyeRadius} fill={eyeColor} />
              <circle
                className="yak-mascot__pupil"
                cx={rightEyeX}
                cy={eyeY}
                r={pupilRadius}
                fill={pupilColor}
              />
            </g>
          </>
        ) : (
          <>
            <circle
              className="yak-mascot__eye yak-mascot__dot-eye"
              cx={leftEyeX}
              cy={eyeY}
              r={eyeRadius}
              fill={pupilColor}
            />
            <circle
              className="yak-mascot__eye yak-mascot__dot-eye"
              cx={rightEyeX}
              cy={eyeY}
              r={eyeRadius}
              fill={pupilColor}
            />
          </>
        )}
      </g>

      {neutralMouth !== "none" ? (
        <path
          className="yak-mascot__mouth yak-mascot__mouth--neutral"
          d={neutralPath}
          fill="none"
          stroke={pupilColor}
          strokeWidth="3"
          strokeLinecap="round"
        />
      ) : null}
      <path
        className="yak-mascot__mouth yak-mascot__mouth--smile"
        d={`M ${centerX - mouthWidth / 2} ${mouthY - 2} Q ${centerX} ${mouthY + 16} ${centerX + mouthWidth / 2} ${mouthY - 2}`}
        fill="none"
        stroke={pupilColor}
        strokeWidth="3.5"
        strokeLinecap="round"
      />
      <circle
        className="yak-mascot__mouth yak-mascot__mouth--surprise"
        cx={centerX}
        cy={mouthY + 4}
        r="7"
        fill="none"
        stroke={pupilColor}
        strokeWidth="3"
      />
    </g>
  );
}

function PurpleMascot() {
  return (
    <g className="yak-mascot yak-mascot--purple">
      <g className="yak-mascot__entry">
        <g className="yak-mascot__breathe">
          <g className="yak-mascot__action">
            <g className="yak-mascot__state">
              <g className="yak-mascot__mouse">
                <path d="M212 550V112Q212 102 222 102H394Q404 102 404 112V550Z" fill="#6128F5" />
                <Face
                  whiteEyes
                  eyeColor="#FFFFFF"
                  pupilColor="#171717"
                  leftEyeX={269}
                  rightEyeX={337}
                  eyeY={142}
                  eyeRadius={8.5}
                  pupilRadius={3.5}
                  mouthY={172}
                  mouthWidth={22}
                />
              </g>
            </g>
          </g>
        </g>
      </g>
    </g>
  );
}

function BlackMascot() {
  return (
    <g className="yak-mascot yak-mascot--black">
      <g className="yak-mascot__entry">
        <g className="yak-mascot__breathe">
          <g className="yak-mascot__action">
            <g className="yak-mascot__state">
              <g className="yak-mascot__mouse">
                <path d="M342 550V250Q342 242 350 242H456Q464 242 464 250V550Z" fill="#191A20" />
                <Face
                  whiteEyes
                  neutralMouth="none"
                  eyeColor="#FFFFFF"
                  pupilColor="#171717"
                  leftEyeX={378}
                  rightEyeX={426}
                  eyeY={276}
                  eyeRadius={7.5}
                  pupilRadius={3.2}
                  mouthY={308}
                  mouthWidth={22}
                />
              </g>
            </g>
          </g>
        </g>
      </g>
    </g>
  );
}

function OrangeMascot() {
  return (
    <g className="yak-mascot yak-mascot--orange">
      <g className="yak-mascot__entry">
        <g className="yak-mascot__breathe">
          <g className="yak-mascot__action">
            <g className="yak-mascot__state">
              <g className="yak-mascot__mouse">
                <path d="M72 550C72 457 144 388 242 388C340 388 410 457 410 550Z" fill="#FF7D2A" />
                <Face
                  eyeColor="#171717"
                  pupilColor="#171717"
                  leftEyeX={192}
                  rightEyeX={252}
                  eyeY={451}
                  eyeRadius={4.2}
                  pupilRadius={4.2}
                  mouthY={483}
                  mouthWidth={22}
                />
              </g>
            </g>
          </g>
        </g>
      </g>
    </g>
  );
}

function YellowMascot() {
  return (
    <g className="yak-mascot yak-mascot--yellow">
      <g className="yak-mascot__entry">
        <g className="yak-mascot__breathe">
          <g className="yak-mascot__action">
            <g className="yak-mascot__state">
              <g className="yak-mascot__mouse">
                <path
                  d="M450 550V405C450 345 483 310 525 310C570 310 598 349 598 405V550Z"
                  fill="#F3D30B"
                />
                <Face
                  neutralMouth="line"
                  eyeColor="#171717"
                  pupilColor="#171717"
                  leftEyeX={492}
                  rightEyeX={535}
                  eyeY={373}
                  eyeRadius={4.2}
                  pupilRadius={4.2}
                  mouthY={410}
                  mouthWidth={52}
                />
              </g>
            </g>
          </g>
        </g>
      </g>
    </g>
  );
}

export function LoginScene({ action, focusedField }: LoginSceneProps) {
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const root = rootRef.current;
    if (!root) return;

    let targetX = 0;
    let targetY = 0;
    let currentX = 0;
    let currentY = 0;
    let frame = 0;

    const onPointerMove = (event: PointerEvent) => {
      const rect = root.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      targetX = clamp((event.clientX - (rect.left + rect.width / 2)) / (rect.width / 2), -1, 1);
      targetY = clamp((event.clientY - (rect.top + rect.height / 2)) / (rect.height / 2), -1, 1);
    };

    const updateVariables = () => {
      currentX += (targetX - currentX) * 0.08;
      currentY += (targetY - currentY) * 0.08;

      root.style.setProperty("--yak-login-eye-x", `${currentX * 8}px`);
      root.style.setProperty("--yak-login-eye-y", `${currentY * 4}px`);
      root.style.setProperty("--yak-login-purple-skew", `${currentX * -5}deg`);
      root.style.setProperty("--yak-login-black-skew", `${currentX * -4}deg`);
      root.style.setProperty("--yak-login-orange-skew", `${currentX * -4}deg`);
      root.style.setProperty("--yak-login-yellow-skew", `${currentX * 3}deg`);
      root.style.setProperty("--yak-login-purple-scale-y", String(1 - currentY * 0.04));
      root.style.setProperty("--yak-login-black-scale-y", String(1 - currentY * 0.03));
      root.style.setProperty("--yak-login-orange-scale-y", String(1 - currentY * 0.03));
      root.style.setProperty("--yak-login-yellow-scale-y", String(1 - currentY * 0.03));

      frame = window.requestAnimationFrame(updateVariables);
    };

    window.addEventListener("pointermove", onPointerMove, { passive: true });
    frame = window.requestAnimationFrame(updateVariables);

    return () => {
      window.removeEventListener("pointermove", onPointerMove);
      window.cancelAnimationFrame(frame);
    };
  }, []);

  useEffect(() => {
    const root = rootRef.current;
    if (!root || action.nonce === 0) return;

    ACTION_CLASSES.forEach((className) => root.classList.remove(className));

    const className = ACTION_CLASS[action.type];
    let timeout = 0;
    const frame = window.requestAnimationFrame(() => {
      root.classList.add(className);
      timeout = window.setTimeout(
        () => root.classList.remove(className),
        ACTION_DURATION[action.type],
      );
    });

    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(timeout);
      root.classList.remove(className);
    };
  }, [action.nonce, action.type]);

  const focusClass =
    focusedField === "userName"
      ? "is-user-focus"
      : focusedField === "userPassword"
        ? "is-password-focus"
        : "";

  return (
    <div ref={rootRef} className={`yak-login-scene ${focusClass}`} aria-hidden="true">
      <svg
        className="yak-login-scene__svg"
        viewBox="0 0 720 580"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <PurpleMascot />
        <BlackMascot />
        <OrangeMascot />
        <YellowMascot />
      </svg>
    </div>
  );
}
