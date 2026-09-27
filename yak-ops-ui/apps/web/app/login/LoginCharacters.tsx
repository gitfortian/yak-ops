import { useEffect, useRef } from "react";

import "./login-characters.css";

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

function PurpleCharacter() {
  return (
    <g data-character="purple" className="yak-login-character yak-login-character--purple">
      <g className="yak-login-character--purple__entry">
        <g className="yak-login-character--purple__breathe">
          <g className="yak-login-character--purple__body">
            <path d="M212 550V112Q212 102 222 102H394Q404 102 404 112V550Z" fill="#6128F5" />
            <g className="yak-login-character--purple__face">
              <circle cx="269" cy="142" r="8.5" fill="#FFFFFF" />
              <circle
                className="yak-login-character--purple__pupil"
                cx="269"
                cy="142"
                r="3.5"
                fill="#171717"
              />
              <circle cx="337" cy="142" r="8.5" fill="#FFFFFF" />
              <circle
                className="yak-login-character--purple__pupil"
                cx="337"
                cy="142"
                r="3.5"
                fill="#171717"
              />
              <path
                d="M292 172Q303 177 314 172"
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
  );
}

function BlackCharacter() {
  return (
    <g data-character="black" className="yak-login-character yak-login-character--black">
      <g className="yak-login-character--black__entry">
        <g className="yak-login-character--black__breathe">
          <g className="yak-login-character--black__body">
            <path d="M342 550V250Q342 242 350 242H456Q464 242 464 250V550Z" fill="#191A20" />
            <g className="yak-login-character--black__face">
              <circle cx="378" cy="276" r="7.5" fill="#FFFFFF" />
              <circle
                className="yak-login-character--black__pupil"
                cx="378"
                cy="276"
                r="3.2"
                fill="#171717"
              />
              <circle cx="426" cy="276" r="7.5" fill="#FFFFFF" />
              <circle
                className="yak-login-character--black__pupil"
                cx="426"
                cy="276"
                r="3.2"
                fill="#171717"
              />
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
          <g className="yak-login-character--yellow__body">
            <path
              d="M450 550V405C450 345 483 310 525 310C570 310 598 349 598 405V550Z"
              fill="#F3D30B"
            />
            <g className="yak-login-character--yellow__face">
              <g className="yak-login-character--yellow__eyes">
                <circle cx="492" cy="373" r="4.2" fill="#171717" />
                <circle cx="535" cy="373" r="4.2" fill="#171717" />
              </g>
              <path
                d="M488 410H539"
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
  );
}

function OrangeCharacter() {
  return (
    <g data-character="orange" className="yak-login-character yak-login-character--orange">
      <g className="yak-login-character--orange__entry">
        <g className="yak-login-character--orange__breathe">
          <g className="yak-login-character--orange__body">
            <path d="M72 550C72 457 144 388 242 388C340 388 410 457 410 550Z" fill="#FF7D2A" />
            <g className="yak-login-character--orange__face">
              <g className="yak-login-character--orange__eyes">
                <circle cx="192" cy="451" r="4.2" fill="#171717" />
                <circle cx="252" cy="451" r="4.2" fill="#171717" />
              </g>
              <path
                d="M211 483Q222 488 233 483"
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
  );
}

export default function LoginCharacters() {
  const sceneRef = useRef<HTMLDivElement | null>(null);

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
      purpleX += (targetX - purpleX) * 0.075;
      purpleY += (targetY - purpleY) * 0.075;

      blackX += (targetX - blackX) * 0.042;
      blackY += (targetY - blackY) * 0.042;

      orangeVelocityX += (targetX - orangeX) * 0.018;
      orangeVelocityY += (targetY - orangeY) * 0.018;
      orangeVelocityX *= 0.82;
      orangeVelocityY *= 0.82;
      orangeX += orangeVelocityX;
      orangeY += orangeVelocityY;

      yellowVelocityX += (targetX - yellowX) * 0.028;
      yellowVelocityY += (targetY - yellowY) * 0.028;
      yellowVelocityX *= 0.76;
      yellowVelocityY *= 0.76;
      yellowX += yellowVelocityX;
      yellowY += yellowVelocityY;

      const orangeSquash = clamp(
        Math.abs(orangeVelocityX) * 0.55 + Math.abs(orangeVelocityY) * 0.35,
        0,
        0.035,
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

      scene.style.setProperty("--yak-orange-shift-x", `${orangeX * 6}px`);
      scene.style.setProperty(
        "--yak-orange-scale-x",
        String(1 + Math.abs(orangeX) * 0.028 + orangeSquash),
      );
      scene.style.setProperty(
        "--yak-orange-scale-y",
        String(1 - Math.abs(orangeX) * 0.014 - orangeY * 0.028 - orangeSquash * 0.65),
      );
      scene.style.setProperty("--yak-orange-face-x", `${orangeX * 9}px`);
      scene.style.setProperty("--yak-orange-face-y", `${orangeY * 4}px`);
      scene.style.setProperty("--yak-orange-eye-x", `${orangeX * 3}px`);
      scene.style.setProperty("--yak-orange-eye-y", `${orangeY * 1.5}px`);

      scene.style.setProperty("--yak-yellow-shift-x", `${yellowX * 7}px`);
      scene.style.setProperty("--yak-yellow-lift-y", `${yellowY * 3}px`);
      scene.style.setProperty("--yak-yellow-lean", `${yellowX * 5.5}deg`);
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

  return (
    <div
      ref={sceneRef}
      className="yak-login-characters flex min-h-screen items-end justify-center overflow-hidden bg-[#efedf2]"
      aria-hidden="true"
    >
      <svg
        className="yak-login-characters__svg h-auto w-[min(88%,820px)]"
        viewBox="0 0 720 580"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <PurpleCharacter />
        <YellowCharacter />
        <BlackCharacter />
        <OrangeCharacter />
      </svg>
    </div>
  );
}
