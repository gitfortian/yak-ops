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
  mouthY,
  mouthWidth,
  whiteEyes = false,
}: {
  eyeColor: string;
  pupilColor: string;
  leftEyeX: number;
  rightEyeX: number;
  eyeY: number;
  mouthY: number;
  mouthWidth: number;
  whiteEyes?: boolean;
}) {
  const centerX = (leftEyeX + rightEyeX) / 2;

  return (
    <g className="yak-mascot__face">
      <g className="yak-mascot__eyes">
        {whiteEyes ? (
          <>
            <g className="yak-mascot__eye">
              <circle cx={leftEyeX} cy={eyeY} r="12" fill={eyeColor} />
              <circle
                className="yak-mascot__pupil"
                cx={leftEyeX}
                cy={eyeY}
                r="4.5"
                fill={pupilColor}
              />
            </g>
            <g className="yak-mascot__eye">
              <circle cx={rightEyeX} cy={eyeY} r="12" fill={eyeColor} />
              <circle
                className="yak-mascot__pupil"
                cx={rightEyeX}
                cy={eyeY}
                r="4.5"
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
              r="6"
              fill={pupilColor}
            />
            <circle
              className="yak-mascot__eye yak-mascot__dot-eye"
              cx={rightEyeX}
              cy={eyeY}
              r="6"
              fill={pupilColor}
            />
          </>
        )}
      </g>

      <path
        className="yak-mascot__mouth yak-mascot__mouth--neutral"
        d={`M ${centerX - mouthWidth / 2} ${mouthY} Q ${centerX} ${mouthY + 7} ${centerX + mouthWidth / 2} ${mouthY}`}
        fill="none"
        stroke={pupilColor}
        strokeWidth="3"
        strokeLinecap="round"
      />
      <path
        className="yak-mascot__mouth yak-mascot__mouth--smile"
        d={`M ${centerX - mouthWidth / 2} ${mouthY - 2} Q ${centerX} ${mouthY + 18} ${centerX + mouthWidth / 2} ${mouthY - 2}`}
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
                <rect x="218" y="126" width="158" height="404" rx="22" fill="#5B3BFF" />
                <Face
                  whiteEyes
                  eyeColor="#FFFFFF"
                  pupilColor="#171717"
                  leftEyeX={267}
                  rightEyeX={327}
                  eyeY={186}
                  mouthY={224}
                  mouthWidth={30}
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
                <rect x="362" y="244" width="108" height="286" rx="18" fill="#181A20" />
                <Face
                  whiteEyes
                  eyeColor="#FFFFFF"
                  pupilColor="#171717"
                  leftEyeX={393}
                  rightEyeX={439}
                  eyeY={293}
                  mouthY={329}
                  mouthWidth={26}
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
                <path
                  d="M82 530V441C82 345 153 294 238 294C323 294 349 360 349 441V530Z"
                  fill="#FF8B2B"
                />
                <Face
                  eyeColor="#171717"
                  pupilColor="#171717"
                  leftEyeX={173}
                  rightEyeX={238}
                  eyeY={406}
                  mouthY={449}
                  mouthWidth={34}
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
                  d="M416 530V412C416 332 466 291 526 291C586 291 620 347 620 412V530Z"
                  fill="#FFD33D"
                />
                <Face
                  eyeColor="#171717"
                  pupilColor="#171717"
                  leftEyeX={479}
                  rightEyeX={535}
                  eyeY={391}
                  mouthY={430}
                  mouthWidth={30}
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
      <div className="yak-login-scene__glow" />
      <div className="yak-login-scene__floor" />

      <svg
        className="yak-login-scene__svg"
        viewBox="0 0 720 620"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <ellipse cx="353" cy="541" rx="258" ry="24" fill="rgba(37, 40, 50, 0.055)" />
        <PurpleMascot />
        <BlackMascot />
        <OrangeMascot />
        <YellowMascot />
      </svg>
    </div>
  );
}
