import { useEffect, useRef } from "react";

const FACE_ORIGIN_X = 79;
const FACE_ORIGIN_Y = 54;
const FACE_MAX_LEFT = 7;
const FACE_MAX_RIGHT = 28;
const FACE_MAX_UP = 4;
const FACE_MAX_DOWN = 14;
const FACE_TRACK_DISTANCE = 150;

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
    let frameId: number | null = null;

    const render = () => {
      if (reduceMotion) {
        currentX = targetX;
        currentY = targetY;
      } else {
        currentX += (targetX - currentX) * 0.12;
        currentY += (targetY - currentY) * 0.12;
      }

      face.setAttribute(
        "transform",
        `translate(${currentX.toFixed(2)} ${currentY.toFixed(2)})`,
      );

      const settled =
        Math.abs(targetX - currentX) < 0.01 && Math.abs(targetY - currentY) < 0.01;
      frameId = settled ? null : window.requestAnimationFrame(render);
    };

    const scheduleRender = () => {
      if (frameId === null) frameId = window.requestAnimationFrame(render);
    };

    const handlePointerMove = (event: PointerEvent) => {
      if (event.pointerType && event.pointerType !== "mouse" && event.pointerType !== "pen") return;

      const matrix = svg.getScreenCTM();
      if (!matrix) return;

      const pointer = svg.createSVGPoint();
      pointer.x = event.clientX;
      pointer.y = event.clientY;
      const localPointer = pointer.matrixTransform(matrix.inverse());

      const deltaX = localPointer.x - FACE_ORIGIN_X;
      const deltaY = localPointer.y - FACE_ORIGIN_Y;
      const distance = Math.hypot(deltaX, deltaY);
      if (distance < 0.01) {
        targetX = 0;
        targetY = 0;
        scheduleRender();
        return;
      }

      const strength = clamp(distance / FACE_TRACK_DISTANCE, 0, 1);
      const directionX = deltaX / distance;
      const directionY = deltaY / distance;

      const horizontalLimit = directionX < 0 ? FACE_MAX_LEFT : FACE_MAX_RIGHT;
      const horizontal = directionX * horizontalLimit * strength;

      const leftEdgeDrop = clamp(-horizontal / FACE_MAX_LEFT, 0, 1) * 7;
      const sideDrop = Math.pow(Math.abs(horizontal) / FACE_MAX_RIGHT, 2) * 2;
      const vertical = directionY * 8 * strength + leftEdgeDrop + sideDrop;

      targetX = horizontal;
      targetY = clamp(vertical, -FACE_MAX_UP, FACE_MAX_DOWN);
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
