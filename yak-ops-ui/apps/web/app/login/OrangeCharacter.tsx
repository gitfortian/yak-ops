import { useEffect, useRef } from "react";

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
        currentX += (targetX - currentX) * 0.16;
        currentY += (targetY - currentY) * 0.16;
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

      const rect = svg.getBoundingClientRect();
      if (!rect.width || !rect.height) return;

      const centerX = rect.left + rect.width / 2;
      const centerY = rect.top + rect.height / 2;
      const x = clamp((event.clientX - centerX) / (rect.width * 0.85), -1, 1);
      const y = clamp((event.clientY - centerY) / (rect.height * 0.9), -1, 1);

      targetX = x * 8;
      targetY = y * 5;
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
      <path
        d="M14 118C14 61 56 17 109 14C166 11 206 58 206 118H14Z"
        fill="#FF7931"
      />
      <g ref={faceRef}>
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
