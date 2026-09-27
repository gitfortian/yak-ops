import { useEffect, useRef } from "react";

const FACE_X_RANGE = 9;
const FACE_Y_RANGE = 5;
const FACE_FOLLOW_RESPONSE = 9;
const FRAME_DELTA_LIMIT_SECONDS = 0.05;
const FACE_SETTLE_EPSILON = 0.01;

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value));
}

export default function OrangeCharacter() {
  const svgRef = useRef<SVGSVGElement | null>(null);
  const faceRef = useRef<SVGGElement | null>(null);

  useEffect(() => {
    const svg = svgRef.current;
    const face = faceRef.current;
    if (!svg || !face) return;

    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    let targetX = 0;
    let targetY = 0;
    let currentX = 0;
    let currentY = 0;
    let previousFrameTime: number | null = null;
    let frameId: number | null = null;

    const applyFaceTransform = () => {
      face.setAttribute(
        "transform",
        `translate(${currentX.toFixed(2)} ${currentY.toFixed(2)})`,
      );
    };

    const render = (frameTime: number) => {
      if (reduceMotion) {
        currentX = targetX;
        currentY = targetY;
      } else {
        const deltaSeconds =
          previousFrameTime === null
            ? 1 / 60
            : Math.min((frameTime - previousFrameTime) / 1000, FRAME_DELTA_LIMIT_SECONDS);
        const follow = 1 - Math.exp(-FACE_FOLLOW_RESPONSE * deltaSeconds);

        currentX += (targetX - currentX) * follow;
        currentY += (targetY - currentY) * follow;
      }

      previousFrameTime = frameTime;

      const settled =
        Math.abs(targetX - currentX) < FACE_SETTLE_EPSILON &&
        Math.abs(targetY - currentY) < FACE_SETTLE_EPSILON;

      if (settled) {
        currentX = targetX;
        currentY = targetY;
      }

      applyFaceTransform();

      if (settled) {
        previousFrameTime = null;
        frameId = null;
        return;
      }

      frameId = window.requestAnimationFrame(render);
    };

    const scheduleRender = () => {
      if (frameId === null) frameId = window.requestAnimationFrame(render);
    };

    const handlePointerMove = (event: PointerEvent) => {
      if (event.pointerType && event.pointerType !== "mouse" && event.pointerType !== "pen") return;

      const trackingArea = svg.parentElement;
      if (!trackingArea) return;

      const rect = trackingArea.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      const normalizedX = clamp(((event.clientX - rect.left) / rect.width) * 2 - 1, -1, 1);
      const normalizedY = clamp(((event.clientY - rect.top) / rect.height) * 2 - 1, -1, 1);

      targetX = normalizedX * FACE_X_RANGE;
      targetY = normalizedY * FACE_Y_RANGE;
      scheduleRender();
    };

    const resetFace = () => {
      targetX = 0;
      targetY = 0;
      scheduleRender();
    };

    window.addEventListener("pointermove", handlePointerMove, { passive: true });
    window.addEventListener("blur", resetFace);
    document.addEventListener("pointerleave", resetFace);

    return () => {
      if (frameId !== null) window.cancelAnimationFrame(frameId);
      window.removeEventListener("pointermove", handlePointerMove);
      window.removeEventListener("blur", resetFace);
      document.removeEventListener("pointerleave", resetFace);
    };
  }, []);

  return (
    <svg
      ref={svgRef}
      aria-label="橙色角色"
      className="h-auto w-[min(34vw,360px)] min-w-[220px]"
      role="img"
      viewBox="0 0 220 128"
    >
      <defs>
        <clipPath id="orange-character-body-clip">
          <path d="M14 118C14 61 56 17 109 14C166 11 206 58 206 118H14Z" />
        </clipPath>
      </defs>
      <path
        d="M14 118C14 61 56 17 109 14C166 11 206 58 206 118H14Z"
        fill="#FF7931"
      />
      <g ref={faceRef} clipPath="url(#orange-character-body-clip)">
        <circle cx="56" cy="43" r="4.8" fill="#171717" />
        <circle cx="98" cy="50" r="4.6" fill="#171717" />
        <path
          d="M69.5 53.2C69.5 52.1 70.6 51.4 71.6 51.8C76.8 53.6 82.5 54.1 87.8 52.8C88.9 52.5 90 53.3 89.8 54.4C88.7 62.3 84.8 66.5 79.5 66.5C74.1 66.5 70.2 62.2 69.2 54.8C69.1 54.2 69.2 53.7 69.5 53.2Z"
          fill="#171717"
        />
      </g>
    </svg>
  );
}
